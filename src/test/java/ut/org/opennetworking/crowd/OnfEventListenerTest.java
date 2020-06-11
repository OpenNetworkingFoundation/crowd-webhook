package ut.org.opennetworking.crowd;

import com.atlassian.crowd.audit.AuditLogChangeset;
import com.atlassian.crowd.audit.query.AuditLogQuery;
import com.atlassian.crowd.embedded.api.Directory;
import com.atlassian.crowd.embedded.api.DirectoryType;
import com.atlassian.crowd.event.user.UserAttributeStoredEvent;
import com.atlassian.crowd.event.user.UserCreatedEvent;
import com.atlassian.crowd.exception.*;
import com.atlassian.crowd.manager.audit.AuditLogConfiguration;
import com.atlassian.crowd.manager.audit.AuditService;
import com.atlassian.crowd.manager.directory.DirectoryManager;
import com.atlassian.crowd.manager.directory.DirectoryPermissionException;
import com.atlassian.crowd.model.directory.ImmutableDirectory;
import com.atlassian.crowd.model.user.ImmutableUser;
import com.atlassian.crowd.model.user.UserTemplateWithAttributes;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opennetworking.crowd.OnfEventListener;
import org.opennetworking.crowd.OnfEventPoster;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.opennetworking.crowd.OnfEventListener.EventType.USER_ADDED;
import static org.opennetworking.crowd.OnfEventListener.EventType.USER_ADDED_GITHUB;

public class OnfEventListenerTest
{
    private MockOnfEventPoster eventPoster;
    private DirectoryManager directoryManager;
    private MockAuditService auditService;
    private OnfEventListener eventListener;
    private static Directory directory = ImmutableDirectory
            .builder("test-directory", DirectoryType.CUSTOM, null)
            .setId(7L)
            .build();

    static class MockOnfEventPoster extends OnfEventPoster {
        List<OnfEventListener.WebhookEvent> events = Lists.newArrayList();

        MockOnfEventPoster() {
            super(null);
        }

        @Override
        public void send(OnfEventListener.WebhookEvent event) {
            // Mock event send
            events.add(event);
        }
    }

    static class MockAuditService implements AuditService {

        @Override
        public void saveAudit(AuditLogChangeset auditLogChangeset) {

        }

        @Override
        public <RESULT> List<RESULT> searchAuditLog(AuditLogQuery<RESULT> auditLogQuery) {
            return Lists.newArrayList();
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public void saveConfiguration(AuditLogConfiguration auditLogConfiguration) {

        }

        @Override
        public AuditLogConfiguration getConfiguration() {
            return null;
        }

        @Override
        public boolean shouldAuditEvent() {
            return false;
        }
    }


    private static UserTemplateWithAttributes getUser(String username, String email, String githubId) {
        UserTemplateWithAttributes userTemplateWithAttributes =
                UserTemplateWithAttributes.toUserWithNoAttributes(
                        ImmutableUser.builder(directory.getId(), username)
                                     .emailAddress(email)
                                     .build());
        userTemplateWithAttributes.setAttribute(OnfEventListener.GITHUB_ID_ATTRIBUTE, githubId);
        return userTemplateWithAttributes;
    }

    @BeforeClass
    public static void setupOnce() {
    }

    @Before
    public void setup() {
        eventPoster = new MockOnfEventPoster();
        directoryManager = new MockDirectoryManager();
        auditService = new MockAuditService();
        eventListener = new OnfEventListener(eventPoster, directoryManager, auditService);
    }

    @Test
    public void testUserAdded() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        UserTemplateWithAttributes user = getUser("test-user", "test@test", null);
        directoryManager.addUser(7, user, null);
        UserCreatedEvent event = new UserCreatedEvent(null, directory, user);
        eventListener.userCreated(event);
        OnfEventListener.WebhookEvent expectedEvent = new OnfEventListener.WebhookEvent();
        expectedEvent.type = USER_ADDED;
        expectedEvent.user = new OnfEventListener.WebhookUser(user);
        System.out.println(expectedEvent);
        assertArrayEquals(Lists.newArrayList(expectedEvent).toArray(), eventPoster.events.toArray());
    }

    @Test
    public void testUserAddedGithub() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String githubId = "test-github";
        UserTemplateWithAttributes user = getUser("test-user", "test@test", githubId);
        directoryManager.addUser(7, user, null);
        Map<String, Set<String>> attributes = ImmutableMap.of(OnfEventListener.GITHUB_ID_ATTRIBUTE, ImmutableSet.of(githubId));
        UserAttributeStoredEvent event = new UserAttributeStoredEvent(null, directory, user, attributes);
        eventListener.userAttributeStored(event);
        OnfEventListener.WebhookEvent expectedEvent = new OnfEventListener.WebhookEvent();
        expectedEvent.type = USER_ADDED_GITHUB;
        expectedEvent.user = new OnfEventListener.WebhookUser(user);
        expectedEvent.newGithubId = githubId;
        System.out.println(expectedEvent);
        assertArrayEquals(Lists.newArrayList(expectedEvent).toArray(), eventPoster.events.toArray());
    }

//    @Test
//    public void testUserDeleted() throws
//            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
//            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
//        UserTemplateWithAttributes user = getUser("test-user", "test@test", "test-github");
//        directoryManager.addUser(7, user, null);
//        UsersDeletedEvent event = new UsersDeletedEvent(null, directory, Lists.newArrayList(user.getName()));
//        eventListener.userDeleted(event);
//        OnfEventListener.WebhookEvent expectedEvent = new OnfEventListener.WebhookEvent();
//        expectedEvent.type = USER_ADDED;
//        expectedEvent.user = new OnfEventListener.WebhookUser(user);
//        assertArrayEquals(Lists.newArrayList(expectedEvent).toArray(), eventPoster.events.toArray());
//    }
}