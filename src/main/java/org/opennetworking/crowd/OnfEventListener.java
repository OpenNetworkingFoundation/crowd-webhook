package org.opennetworking.crowd;

import com.atlassian.crowd.audit.*;
import com.atlassian.crowd.audit.query.AuditLogQuery;
import com.atlassian.crowd.audit.query.AuditLogQueryBuilder;
import com.atlassian.crowd.audit.query.AuditLogQueryEntityRestriction;
import com.atlassian.crowd.event.group.GroupMembershipDeletedEvent;
import com.atlassian.crowd.event.group.GroupMembershipsCreatedEvent;
import com.atlassian.crowd.event.user.*;
import com.atlassian.crowd.exception.DirectoryNotFoundException;
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
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class OnfEventListener {
    private final static Logger logger = LoggerFactory.getLogger(OnfEventListener.class);

    public static String EMAIL_ATTRIBUTE = "Email";
    public static String GITHUB_ID_ATTRIBUTE = "github_id";

    @ComponentImport
    private final DirectoryManager directoryManager;

    // https://developer.atlassian.com/server/framework/atlassian-sdk/store-and-retrieve-plugin-data/
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    @ComponentImport
    private final AuditService auditService;

    @Inject
    public OnfEventListener(final DirectoryManager directoryManager,
                            final PluginSettingsFactory pluginSettingsFactory,
                            final AuditService auditService)
    {
        this.directoryManager = directoryManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
//        PluginSettings globalSettings = this.pluginSettingsFactory.createGlobalSettings();
//        globalSettings.put("bocon.send", "send");
        this.auditService = auditService;
    }

    public static class WebhookUser {
        String username;
        String email;
        String name; // display name
        List<String> groups;
        String githubId;

        WebhookUser(UserWithAttributes user) {
            username = user.getName();
            email = user.getEmailAddress();
            name = user.getDisplayName();
            if (user.getKeys().contains(GITHUB_ID_ATTRIBUTE)) {
                githubId = user.getValue(GITHUB_ID_ATTRIBUTE);
            }
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
        EventType type;
        WebhookUser user;
        String groupName;
        String oldGithubId;
        String newGithubId;
        String oldEmail;
        String newEmail;
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
            // TODO grabbing the first value for now
            String newValue = event.getAttributeValues(GITHUB_ID_ATTRIBUTE).iterator().next();
            AuditLogEntry entry = createAuditEntry(event.getUser().getName(), GITHUB_ID_ATTRIBUTE,
                                                   newValue, event.getTimestamp());
            boolean isUpdated = !Strings.isNullOrEmpty(entry.getOldValue());
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = isUpdated ? EventType.USER_UPDATED_GITHUB: EventType.USER_ADDED_GITHUB;
            webhookEvent.user = user;
            if (isUpdated) {
                webhookEvent.oldGithubId = entry.getOldValue();
            }
            webhookEvent.newGithubId = entry.getNewValue();
            this.sendEvent(webhookEvent);
        }
//        event.getAttributeNames().forEach(key -> {
//            System.out.println(key + ": " + event.getAttributeValues(key).iterator().next());
//        });
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
            AuditLogEntry entry = createAuditEntry(event.getUser().getName(), GITHUB_ID_ATTRIBUTE, "", event.getTimestamp());
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
          WebhookUser added to group

          Actions:
           - add the user to the appropriate external teams
         */
        if (event.getMembershipType() == MembershipType.GROUP_USER) {
            String group = event.getGroupName();
            event.getEntityNames().forEach(username -> {
                WebhookUser user = this.getUser(event.getDirectoryId(), username);
                WebhookEvent webhookEvent = new WebhookEvent();
                webhookEvent.type = EventType.USER_ADDED_GROUP;
                webhookEvent.user = user;
                webhookEvent.groupName = group;
                this.sendEvent(webhookEvent);
            });
        }
    }

    @EventListener
    public void groupMemberDeleted(GroupMembershipDeletedEvent event) {
        /*
          WebhookUser removed from group

          Actions:
           - remove the user to the appropriate external teams
         */
        if (event.getMembershipType() == MembershipType.GROUP_USER) {
            WebhookUser user = this.getUser(event.getDirectoryId(), event.getEntityName());
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.type = EventType.USER_DELETED_GROUP;
            webhookEvent.user = user;
            webhookEvent.groupName = event.getGroupName();
            this.sendEvent(webhookEvent);
        }
    }



    private WebhookUser getUser(long directoryId, String username) {
        WebhookUser user = null;
        try {
            user = new WebhookUser(directoryManager.findUserWithAttributesByName(directoryId, username));
            user.groups = directoryManager.searchNestedGroupRelationships(directoryId,
                    QueryBuilder.queryFor(String.class, EntityDescriptor.group())
                            .parentsOf(EntityDescriptor.user())
                            .withName(username)
                            .returningAtMost(1000));
        } catch (DirectoryNotFoundException e) {
            logger.error("Crowd directory not found", e);
        } catch (UserNotFoundException e) { //
            logger.error("User not found: {}", username);
        } catch (OperationFailedException e) {
            logger.error("Get user operations failed", e);
        }
        return user;
    }

    private void sendEvent(WebhookEvent event) {
//        Gson gson = new Gson();
//        System.out.println(gson.toJson(event));
        OnfEventPoster.send(event);
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
                .findFirst()
                .ifPresent(entity::setEntityId);
        changeset.setEntities(ImmutableSet.of(entity));

        this.auditService.saveAudit(changeset);
        return entry;
    }
}