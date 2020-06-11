package org.opennetworking.crowd;

import com.atlassian.crowd.audit.*;
import com.atlassian.crowd.audit.query.AuditLogQuery;
import com.atlassian.crowd.audit.query.AuditLogQueryBuilder;
import com.atlassian.crowd.audit.query.AuditLogQueryEntityRestriction;
import com.atlassian.crowd.event.group.GroupMembershipDeletedEvent;
import com.atlassian.crowd.event.group.GroupMembershipsCreatedEvent;
import com.atlassian.crowd.event.user.*;
import com.atlassian.crowd.exception.DirectoryNotFoundException;
import com.atlassian.crowd.exception.GroupNotFoundException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.manager.audit.AuditService;
import com.atlassian.crowd.manager.directory.DirectoryManager;
import com.atlassian.crowd.model.audit.AuditLogChangesetEntity;
import com.atlassian.crowd.model.audit.AuditLogEntityEntity;
import com.atlassian.crowd.model.audit.AuditLogEntryEntity;
import com.atlassian.crowd.model.membership.MembershipType;
import com.atlassian.crowd.model.user.UserWithAttributes;
import com.atlassian.crowd.search.EntityDescriptor;
import com.atlassian.crowd.search.builder.QueryBuilder;
import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Strings.isNullOrEmpty;

public class OnfEventListener {
    private static final Logger logger = LoggerFactory.getLogger(OnfEventListener.class);

    public static final String EMAIL_ATTRIBUTE = "Email";
    public static final String GITHUB_ID_ATTRIBUTE = "github_id";
    public static final int MAX_RESULTS = 1000;

    @ComponentImport
    private final OnfEventPoster eventPoster;

    @ComponentImport
    private final DirectoryManager directoryManager;

    @ComponentImport
    private final AuditService auditService;

    @Inject
    public OnfEventListener(final OnfEventPoster eventPoster,
                            final DirectoryManager directoryManager,
                            final AuditService auditService)
    {
        this.eventPoster = eventPoster;
        this.directoryManager = directoryManager;
        this.auditService = auditService;
    }

    public static class WebhookUser {
        String username;
        String email;
        String name; // display name
        List<String> groups;
        String githubId;

        public WebhookUser(UserWithAttributes user) {
            username = user.getName();
            email = user.getEmailAddress();
            name = user.getDisplayName();
            groups = ImmutableList.of();
            if (user.getKeys().contains(GITHUB_ID_ATTRIBUTE)) {
                githubId = user.getValue(GITHUB_ID_ATTRIBUTE);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WebhookUser)) return false;
            WebhookUser that = (WebhookUser) o;
            return equal(username, that.username) &&
                    equal(email, that.email) &&
                    equal(name, that.name) &&
                    equal(groups, that.groups) &&
                    equal(githubId, that.githubId);
        }

        @Override
        public int hashCode() {
            return com.google.common.base.Objects.hashCode(username, email, name, groups, githubId);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("username", username)
                    .add("email", email)
                    .add("name", name)
                    .add("groups", groups)
                    .add("githubId", githubId)
                    .toString();
        }
    }

    public enum EventType {
        USER_ADDED,
        USER_ADDED_GITHUB,
        USER_ADDED_GROUP,
        USER_UPDATED_EMAIL,
        USER_UPDATED_GITHUB,
        USER_DELETED,
        USER_DELETED_GITHUB,
        USER_DELETED_GROUP,
    }

    public static class WebhookEvent {
        public EventType type;
        public WebhookUser user;
        public String groupName;
        public String oldGithubId;
        public String newGithubId;
        public String oldEmail;
        public String newEmail;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WebhookEvent)) return false;
            WebhookEvent that = (WebhookEvent) o;
            return type == that.type &&
                    equal(user, that.user) &&
                    equal(groupName, that.groupName) &&
                    equal(oldGithubId, that.oldGithubId) &&
                    equal(newGithubId, that.newGithubId) &&
                    equal(oldEmail, that.oldEmail) &&
                    equal(newEmail, that.newEmail);
        }

        @Override
        public int hashCode() {
            return com.google.common.base.Objects.hashCode(
                    type, user, groupName, oldGithubId, newGithubId, oldEmail, newEmail);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("type", type)
                    .add("user", user)
                    .add("groupName", groupName)
                    .add("oldGithubId", oldGithubId)
                    .add("newGithubId", newGithubId)
                    .add("oldEmail", oldEmail)
                    .add("newEmail", newEmail)
                    .toString();
        }
    }

//    @EventListener
//    public void printDirectoryEvent(DirectoryEvent event) {
//        System.out.println("Got event: " + event.toString());
//    }

    @EventListener
    public void userCreated(UserCreatedEvent event) {
        /*
          Actions:
           - Validate email address
           - Check group membership (in case one was auto-added)
         */
        WebhookUser user = getUser(event.getDirectoryId(), event.getUser().getName());
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.type = EventType.USER_ADDED;
        webhookEvent.user = user;
        this.sendEvent(webhookEvent);
    }

    @EventListener
    public void userDeleted(UsersDeletedEvent event) {
        /*
          Actions:
           - Remove users from external groups / teams
         */
        event.getUsernames().forEach(username -> {
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = EventType.USER_DELETED;
            getAuditEntry(username, GITHUB_ID_ATTRIBUTE).ifPresent(entry ->
                    webhookEvent.oldGithubId = entry.getNewValue());
            getAuditEntry(username, EMAIL_ATTRIBUTE).ifPresent(entry ->
                    webhookEvent.oldEmail = entry.getOldValue()); // user deleted will be most recent in audit log
            this.sendEvent(webhookEvent);
        });
    }

    @EventListener
    public void userEmailUpdated(UserEmailChangedEvent event) {
        /*
          Actions:
           - Validate email address
         */
        WebhookUser user = getUser(event.getDirectoryId(), event.getUser().getName());
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.type = EventType.USER_UPDATED_EMAIL;
        webhookEvent.user = user;
        webhookEvent.newEmail = user.email; // assert user.email == entry.getOldValue
        getAuditEntry(event.getUser().getName(), EMAIL_ATTRIBUTE).ifPresent(entry ->
                webhookEvent.oldEmail = entry.getOldValue());
        this.sendEvent(webhookEvent);
    }

    @EventListener
    public void userAttributeStored(UserAttributeStoredEvent event) {
        /*
          GitHub user could have been added; if so, it will be in the event

          Actions:
           - Add the new (and if available, remove the old) GitHub ID from Github Teams
             if the user is a member of the right Crowd group
         */
        // Note: event.getAttributeValues(key) only contains updated attributes
        if (event.getAttributeNames().contains(GITHUB_ID_ATTRIBUTE)) {
            WebhookUser user = getUser(event.getDirectoryId(), event.getUser().getName());
            // TODO grabbing the first value for now; we don't support multiple Github IDs
            // In some cases, it seems like getAttributeValues returns an empty collection; not sure why?
            String newValue = event.getAttributeValues(GITHUB_ID_ATTRIBUTE).stream().findFirst()
                                   .orElseGet(() -> {
                                       logger.warn("Event missing Github ID -- user: {} / github id: {}",
                                                   user.name, user.githubId);
                                       return ""; // return empty string for now
                                   });
            AuditLogEntry entry = createAuditEntry(event.getUser().getName(), GITHUB_ID_ATTRIBUTE,
                                                   newValue, event.getTimestamp());
            boolean isUpdated = !isNullOrEmpty(entry.getOldValue());
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = isUpdated ? EventType.USER_UPDATED_GITHUB: EventType.USER_ADDED_GITHUB;
            webhookEvent.user = user;
            if (isUpdated) {
                webhookEvent.oldGithubId = entry.getOldValue();
            }
            webhookEvent.newGithubId = entry.getNewValue();
            this.sendEvent(webhookEvent);
        }
    }

    @EventListener
    public void userAttributeDeleted(UserAttributeDeletedEvent event) {
        /*
          GitHub user could have been deleted; if so, it will be in the event

          Actions:
           - Remove the GitHub ID from Github Teams
         */
        if (event.getAttributeName().equals(GITHUB_ID_ATTRIBUTE)) {
            WebhookUser user = getUser(event.getDirectoryId(), event.getUser().getName());
            AuditLogEntry entry = createAuditEntry(
                    event.getUser().getName(), GITHUB_ID_ATTRIBUTE, "", event.getTimestamp());
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = EventType.USER_DELETED_GITHUB;
            webhookEvent.user = user;
            webhookEvent.oldGithubId = entry.getOldValue();
            this.sendEvent(webhookEvent);
        }
    }

    @EventListener
    public void groupMembersCreated(GroupMembershipsCreatedEvent event) {
        /*
          User or nested group added to group

          Actions:
           - add the user to the appropriate external teams
         */
        final Long directoryId = event.getDirectoryId();
        final Set<String> groupAndParents = getGroupAndParents(directoryId, event.getGroupName());

        final Collection<String> users;
        if (event.getMembershipType() == MembershipType.GROUP_USER) {
            users = event.getEntityNames();
        } else if (event.getMembershipType() == MembershipType.GROUP_GROUP) {
            // Collect the users from each newly added group
            users = event.getEntityNames().stream()
                    .flatMap(groupName -> 
                        Objects.requireNonNull(getNestedGroupUsers(directoryId, groupName)).stream())
                    .collect(Collectors.toSet());
        } else {
            users = ImmutableList.of();
        }

        // Generate an event for each user that has been directly or indirectly added the group and its parents
        users.forEach(username ->
            groupAndParents.forEach(groupName -> {
                WebhookUser user = this.getUser(directoryId, username);
                WebhookEvent webhookEvent = new WebhookEvent();
                webhookEvent.type = EventType.USER_ADDED_GROUP;
                webhookEvent.user = user;
                webhookEvent.groupName = groupName;
                this.sendEvent(webhookEvent);
            })
        );
    }

    @EventListener
    public void groupMemberDeleted(GroupMembershipDeletedEvent event) {
        /*
          WebhookUser removed from group

          Actions:
           - remove the user to the appropriate external teams
         */
        final Long directoryId = event.getDirectoryId();
        final Set<String> groupAndParents = this.getGroupAndParents(directoryId, event.getGroupName());


        final List<String> users;
        if (event.getMembershipType() == MembershipType.GROUP_USER) {
            users = ImmutableList.of(event.getEntityName());
        } else if (event.getMembershipType() == MembershipType.GROUP_GROUP) {
            // Collect the users from each newly removed group
            users = getNestedGroupUsers(directoryId, event.getEntityName());
        } else {
            users = ImmutableList.of();
        }

        users.forEach(username ->
            groupAndParents.forEach(groupName -> {
                WebhookUser user = this.getUser(directoryId, username);
                if (user.groups.contains(groupName)) {
                    // user is still a number of the group through another group / nested group
                    return; // skip this event
                }
                WebhookEvent webhookEvent = new WebhookEvent();
                webhookEvent.type = EventType.USER_DELETED_GROUP;
                webhookEvent.user = user;
                webhookEvent.groupName = groupName;
                this.sendEvent(webhookEvent);
            })
        );
    }



    private WebhookUser getUser(long directoryId, String username) {
        WebhookUser user = null;
        try {
            user = new WebhookUser(directoryManager.findUserWithAttributesByName(directoryId, username));
            user.groups = directoryManager.searchNestedGroupRelationships(directoryId,
                    QueryBuilder.queryFor(String.class, EntityDescriptor.group())
                            .parentsOf(EntityDescriptor.user())
                            .withName(username)
                            .returningAtMost(MAX_RESULTS));
        } catch (DirectoryNotFoundException e) {
            logger.error("Crowd directory not found", e);
        } catch (UserNotFoundException e) {
            logger.error("User not found: {}", username);
        } catch (OperationFailedException e) {
            logger.error("Get user operations failed", e);
        }
        return user;
    }

    private Set<String> getGroupAndParents(long directoryId, String groupName) {
        Set<String> groups = Sets.newHashSet();
        try {
            groups.add(directoryManager.findGroupByName(directoryId, groupName).getName());
            groups.addAll(directoryManager.searchNestedGroupRelationships(directoryId,
                    QueryBuilder.queryFor(String.class, EntityDescriptor.group())
                            .parentsOf(EntityDescriptor.group())
                            .withName(groupName)
                            .returningAtMost(MAX_RESULTS)));
        } catch (DirectoryNotFoundException e) {
            logger.error("Crowd directory not found", e);
        } catch (GroupNotFoundException e) {
            logger.error("Group not found: {}", groupName);
        } catch (OperationFailedException e) {
            logger.error("Get group operations failed", e);
        }
        return groups;
    }

    private List<String> getNestedGroupUsers(long directoryId, String groupName) {
        try {
            return directoryManager.searchNestedGroupRelationships(directoryId,
                    QueryBuilder.queryFor(String.class, EntityDescriptor.user())
                            .childrenOf(EntityDescriptor.group())
                            .withName(groupName)
                            .returningAtMost(MAX_RESULTS));
        } catch (DirectoryNotFoundException e) {
            logger.error("Crowd directory not found", e);
        } catch (OperationFailedException e) {
            logger.error("Get group operations failed", e);
        }
        return ImmutableList.of();
    }

    private void sendEvent(WebhookEvent event) {
        eventPoster.send(event);
    }

    private Optional<? extends AuditLogEntry> getAuditEntry(String username, String attribute) {
        return getAuditEntry(getAuditChangesets(username), attribute);
    }

    private List<AuditLogChangeset> getAuditChangesets(String username) {
        AuditLogQuery<AuditLogChangeset> query = AuditLogQueryBuilder.queryFor(AuditLogChangeset.class)
                .setUsers(ImmutableList.of(AuditLogQueryEntityRestriction.name(username)))
//              .setProjection(AuditLogChangesetProjection.ENTITY_USER)
                .build();

        // Find the most recent entry for the given attribute
        return this.auditService.searchAuditLog(query);
    }

    private Optional<? extends AuditLogEntry> getAuditEntry(List<AuditLogChangeset> changes, String attribute) {
        // Find the most recent entry for the given attribute
        return changes.stream()
                .flatMap(cs -> cs.getEntries().stream())
                .filter(e -> e.getPropertyName().equals(attribute))
                .findFirst();
    }

    private AuditLogEntry createAuditEntry(String username, String attribute, String newValue, Long timestamp) {
        List<AuditLogChangeset> changesets = getAuditChangesets(username);
        Optional<? extends AuditLogEntry> lastEntry = getAuditEntry(changesets, attribute);
        String oldValue = lastEntry.isPresent() ? lastEntry.get().getNewValue() : "";

        AuditLogChangesetEntity changeset = new AuditLogChangesetEntity();
        changeset.setAuthorType(AuditLogAuthorType.PLUGIN);
        changeset.setAuthorName("onf-event-handler");

        changeset.setTimestamp(timestamp);
        changeset.setSource(AuditLogEventSource.MANUAL);
        changeset.setEventType(AuditLogEventType.USER_UPDATED);

        AuditLogEntryEntity entry = new AuditLogEntryEntity(attribute, oldValue, newValue);
        changeset.setEntries(ImmutableSet.of(entry));

        AuditLogEntityEntity entity = new AuditLogEntityEntity();
        entity.setEntityType(AuditLogEntityType.USER);
        entity.setEntityName(username);
        // Fill in the entity ID for Crowd UI
        changesets.stream()
                .map(AuditLogChangeset::getEntity)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(e -> Objects.equals(e.getEntityName(), username))
                .map(AuditLogEntity::getEntityId)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(entity::setEntityId);
        changeset.setEntities(ImmutableSet.of(entity));

        this.auditService.saveAudit(changeset);
        return entry;
    }
}