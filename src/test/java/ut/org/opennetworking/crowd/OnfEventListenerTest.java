package ut.org.opennetworking.crowd;

import com.atlassian.crowd.embedded.api.Directory;
import com.atlassian.crowd.embedded.api.DirectoryType;
import com.atlassian.crowd.event.user.UserAttributeDeletedEvent;
import com.atlassian.crowd.event.user.UserAttributeStoredEvent;
import com.atlassian.crowd.event.user.UserCreatedEvent;
import com.atlassian.crowd.event.user.UsersDeletedEvent;
import com.atlassian.crowd.exception.*;
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
import org.opennetworking.crowd.api.WebhookEvent;
import org.opennetworking.crowd.api.WebhookUser;
import org.opennetworking.crowd.api.WebhookEvent.EventType;
import org.opennetworking.crowd.listener.OnfEventListener;
import org.opennetworking.crowd.api.OnfEventPoster;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opennetworking.crowd.api.WebhookEvent.EventType.*;
import static org.opennetworking.crowd.api.WebhookUser.GITHUB_ID_ATTRIBUTE;

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

    static class MockOnfEventPoster implements OnfEventPoster {
        List<WebhookEvent> events = Lists.newArrayList();

        @Override
        public void send(WebhookEvent event) {
            // Mock event send
            events.add(event);
        }
    }

    private static UserTemplateWithAttributes getUser(String username, String email, String githubId) {
        UserTemplateWithAttributes userTemplateWithAttributes =
                UserTemplateWithAttributes.toUserWithNoAttributes(
                        ImmutableUser.builder(directory.getId(), username)
                                     .emailAddress(email)
                                     .build());
        userTemplateWithAttributes.setAttribute(GITHUB_ID_ATTRIBUTE, githubId);
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
        String username = "test-user";
        UserTemplateWithAttributes user = getUser(username, "test@test", null);
        directoryManager.addUser(7, user, null);
        UserCreatedEvent event = new UserCreatedEvent(null, directory, user);
        eventListener.userCreated(event);
        WebhookEvent expectedEvent = new WebhookEvent();
        expectedEvent.type = USER_ADDED;
        expectedEvent.user = new WebhookUser(user);
        assertArrayEquals(Lists.newArrayList(expectedEvent).toArray(), eventPoster.events.toArray());
        assertEquals(null, auditService.entries.get(username));
    }

    private UserTemplateWithAttributes AddUserWithGithub(String username, String githubId) throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        UserTemplateWithAttributes user = getUser(username, "test@test", githubId);
        directoryManager.addUser(7, user, null);
        Map<String, Set<String>> attributes = ImmutableMap.of(GITHUB_ID_ATTRIBUTE, ImmutableSet.of(githubId));
        UserAttributeStoredEvent event = new UserAttributeStoredEvent(null, directory, user, attributes);
        eventListener.userAttributeStored(event);
        return user;
    }

    @Test
    public void testUserAddedGithub() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String username = "test-user";
        String githubId = "test-github";
        UserTemplateWithAttributes user = AddUserWithGithub(username, githubId);
        WebhookEvent expectedEvent = new WebhookEvent();
        expectedEvent.type = USER_ADDED_GITHUB;
        expectedEvent.user = new WebhookUser(user);
        expectedEvent.newGithubId = githubId;
        assertArrayEquals(Lists.newArrayList(expectedEvent).toArray(), eventPoster.events.toArray());
        assertEquals(1, auditService.entries.get(username).size());
    }

    @Test
    public void testUserModifiedGithub() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String username = "test-user";
        String oldGithubId = "test-github";
        UserTemplateWithAttributes user = AddUserWithGithub(username, oldGithubId);
        String newGithubId = "test-github2";
        Map<String, Set<String>> attributes = ImmutableMap.of(GITHUB_ID_ATTRIBUTE, ImmutableSet.of(newGithubId));
        UserAttributeStoredEvent event = new UserAttributeStoredEvent(null, directory, user, attributes);
        eventListener.userAttributeStored(event);
        WebhookEvent expectedEvent = new WebhookEvent();
        expectedEvent.type = USER_UPDATED_GITHUB;
        expectedEvent.user = new WebhookUser(user);
        expectedEvent.oldGithubId = oldGithubId;
        expectedEvent.newGithubId = newGithubId;
        assertEquals(2, eventPoster.events.size());
        assertEquals(expectedEvent, eventPoster.events.get(1));
        assertEquals(2, auditService.entries.get(username).size());
    }

    @Test
    public void testUserModifiedGithubNoop() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String username = "test-user";
        String oldGithubId = "test-github";
        UserTemplateWithAttributes user = AddUserWithGithub(username, oldGithubId);
        // Github ID is not updated
        Map<String, Set<String>> attributes = ImmutableMap.of(GITHUB_ID_ATTRIBUTE, ImmutableSet.of(oldGithubId));
        UserAttributeStoredEvent event = new UserAttributeStoredEvent(null, directory, user, attributes);
        eventListener.userAttributeStored(event);
        assertEquals(1, eventPoster.events.size());
        assertEquals(1, auditService.entries.get(username).size());
    }

    @Test
    public void testUserDeletedGithub() throws
            DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
            InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String username = "test-user";
        String oldGithubId = "test-github";
        UserTemplateWithAttributes user = AddUserWithGithub(username, oldGithubId);
        UserAttributeDeletedEvent event = new UserAttributeDeletedEvent(null, directory, user, GITHUB_ID_ATTRIBUTE);
        eventListener.userAttributeDeleted(event);
        WebhookEvent expectedEvent = new WebhookEvent();
        expectedEvent.type = USER_DELETED_GITHUB;
        expectedEvent.user = new WebhookUser(user);
        expectedEvent.oldGithubId = oldGithubId;
        assertEquals(2, eventPoster.events.size());
        assertEquals(expectedEvent, eventPoster.events.get(1));
        assertEquals(2, auditService.entries.get(username).size());
    }

   @Test
   public void testUserDeleted() throws
           DirectoryPermissionException, DirectoryNotFoundException, InvalidCredentialException,
           InvalidUserException, OperationFailedException, UserAlreadyExistsException {
        String username = "test-user";
        String githubId = "test-github";
        UserTemplateWithAttributes user = AddUserWithGithub(username, githubId);
        UsersDeletedEvent event = new UsersDeletedEvent(null, directory, Lists.newArrayList(user.getName()));
        eventListener.userDeleted(event);
        WebhookEvent expectedEvent = new WebhookEvent();
        expectedEvent.type = USER_DELETED;
        expectedEvent.oldGithubId = githubId;
        assertEquals(2, eventPoster.events.size());
        assertEquals(expectedEvent, eventPoster.events.get(1));
        assertEquals(2, auditService.entries.get(username).size());
   }
}