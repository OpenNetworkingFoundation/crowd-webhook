package org.opennetworking.crowd.listener;

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
import com.atlassian.crowd.search.EntityDescriptor;
import com.atlassian.crowd.search.builder.QueryBuilder;
import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.opennetworking.crowd.api.OnfEventPoster;
import org.opennetworking.crowd.api.WebhookEvent;
import org.opennetworking.crowd.api.WebhookUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.opennetworking.crowd.api.WebhookEvent.EventType.*;
import static org.opennetworking.crowd.api.WebhookUser.EMAIL_ATTRIBUTE;
import static org.opennetworking.crowd.api.WebhookUser.GITHUB_ID_ATTRIBUTE;

public class OnfEventListener {
    private static final Logger logger = LoggerFactory.getLogger(OnfEventListener.class);


    public static final int MAX_RESULTS = 1000;

//    @ComponentImport
    private final OnfEventPoster onfEventPoster;

    @ComponentImport
    private final DirectoryManager directoryManager;

    @ComponentImport
    private final AuditService auditService;

    @Inject
    public OnfEventListener(final OnfEventPoster onfEventPoster,
                            final DirectoryManager directoryManager,
                            final AuditService auditService)
    {
        this.onfEventPoster = onfEventPoster;
        this.directoryManager = directoryManager;
        this.auditService = auditService;
    }

    @EventListener
    public void userCreated(UserCreatedEvent event) {
        /*
          Actions:
           - Validate email address
           - Check group membership (in case one was auto-added)
         */
        WebhookUser user = getUser(event.getDirectoryId(), event.getUser().getName());
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.type = USER_ADDED;
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
            webhookEvent.type = USER_DELETED;
            AuditLogEntry entry = createAuditEntry(
                username, GITHUB_ID_ATTRIBUTE, "", event.getTimestamp());
            System.out.println(entry);
            if (entry != null && !isNullOrEmpty(entry.getOldValue())) {
                webhookEvent.oldGithubId = entry.getOldValue();
            }  // else, no old Github ID
            // TODO(bocon): fix email audit log
            getAuditEntry(username, EMAIL_ATTRIBUTE).ifPresent(e ->
                    webhookEvent.oldEmail = e.getOldValue()); // user deleted will be most recent in audit log
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
        webhookEvent.type = USER_UPDATED_EMAIL;
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
            if (entry == null) {
                return;  // Github ID was not updated
            }
            boolean isUpdated = !isNullOrEmpty(entry.getOldValue());
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = isUpdated ? USER_UPDATED_GITHUB: USER_ADDED_GITHUB;
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
            if (entry == null || isNullOrEmpty(entry.getOldValue())) {
                return;  // no old Github ID
            }
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = USER_DELETED_GITHUB;
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
                webhookEvent.type = USER_ADDED_GROUP;
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
                webhookEvent.type = USER_DELETED_GROUP;
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
        onfEventPoster.send(event);
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

        if (oldValue.equals(newValue)) {
            // Old and new Github ID are identical; no need to create an audit log entry.
            return null;
        }

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