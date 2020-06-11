package ut.org.opennetworking.crowd;

import com.atlassian.crowd.embedded.api.Directory;
import com.atlassian.crowd.embedded.api.DirectorySynchronisationInformation;
import com.atlassian.crowd.embedded.api.PasswordCredential;
import com.atlassian.crowd.exception.*;
import com.atlassian.crowd.manager.avatar.AvatarReference;
import com.atlassian.crowd.manager.directory.BulkAddResult;
import com.atlassian.crowd.manager.directory.DirectoryManager;
import com.atlassian.crowd.manager.directory.DirectoryPermissionException;
import com.atlassian.crowd.manager.directory.SynchronisationMode;
import com.atlassian.crowd.model.application.Application;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.group.GroupTemplate;
import com.atlassian.crowd.model.group.GroupWithAttributes;
import com.atlassian.crowd.model.user.*;
import com.atlassian.crowd.search.query.entity.EntityQuery;
import com.atlassian.crowd.search.query.membership.MembershipQuery;
import com.atlassian.crowd.util.BoundedCount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class MockDirectoryManager implements DirectoryManager {

    Map<String, UserWithAttributes> users = Maps.newHashMap();

    @Override
    public UserWithAttributes addUser(long l, UserTemplateWithAttributes userTemplateWithAttributes, PasswordCredential passwordCredential) throws InvalidCredentialException, InvalidUserException, DirectoryPermissionException, DirectoryNotFoundException, OperationFailedException, UserAlreadyExistsException {
        users.put(userTemplateWithAttributes.getName(), userTemplateWithAttributes);
        return userTemplateWithAttributes;
    }

    @Override
    public UserWithAttributes findUserWithAttributesByName(long l, String username) throws DirectoryNotFoundException, UserNotFoundException, OperationFailedException {
        return users.get(username);
    }

    @Override
    public <T> List<T> searchNestedGroupRelationships(long l, MembershipQuery<T> membershipQuery) throws DirectoryNotFoundException, OperationFailedException {
        return ImmutableList.of();
    }

    //-----------------------

    @Override
    public Directory addDirectory(Directory directory) throws DirectoryInstantiationException {
        return null;
    }

    @Override
    public Directory findDirectoryById(long l) throws DirectoryNotFoundException {
        return null;
    }

    @Override
    public List<Directory> findAllDirectories() {
        return null;
    }

    @Override
    public List<Directory> searchDirectories(EntityQuery<Directory> entityQuery) {
        return null;
    }

    @Override
    public Directory findDirectoryByName(String s) throws DirectoryNotFoundException {
        return null;
    }

    @Override
    public Directory updateDirectory(Directory directory) throws DirectoryNotFoundException {
        return null;
    }

    @Override
    public void removeDirectory(Directory directory) throws DirectoryNotFoundException, DirectoryCurrentlySynchronisingException {

    }

    @Override
    public User authenticateUser(long l, String s, PasswordCredential passwordCredential) throws OperationFailedException, InactiveAccountException, InvalidAuthenticationException, ExpiredCredentialException, DirectoryNotFoundException, UserNotFoundException {
        return null;
    }

    @Override
    public User findUserByName(long l, String s) throws DirectoryNotFoundException, UserNotFoundException, OperationFailedException {
        return null;
    }

    @Nonnull
    @Override
    public User findRemoteUserByName(Long aLong, String s) throws OperationFailedException, DirectoryNotFoundException, UserNotFoundException {
        return null;
    }

    @Override
    public User findUserByExternalId(long l, String s) throws DirectoryNotFoundException, UserNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public UserWithAttributes findUserWithAttributesByExternalId(long l, String s) throws DirectoryNotFoundException, UserNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public <T> List<T> searchUsers(long l, EntityQuery<T> entityQuery) throws DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public User addUser(long l, UserTemplate userTemplate, PasswordCredential passwordCredential) throws InvalidCredentialException, InvalidUserException, DirectoryPermissionException, DirectoryNotFoundException, OperationFailedException, UserAlreadyExistsException {
        return null;
    }

    @Override
    public User updateUser(long l, UserTemplate userTemplate) throws DirectoryNotFoundException, UserNotFoundException, DirectoryPermissionException, InvalidUserException, OperationFailedException {
        return null;
    }

    @Override
    public User renameUser(long l, String s, String s1) throws DirectoryNotFoundException, UserNotFoundException, OperationFailedException, DirectoryPermissionException, InvalidUserException, UserAlreadyExistsException {
        return null;
    }

    @Override
    public void storeUserAttributes(long l, String s, Map<String, Set<String>> map) throws DirectoryPermissionException, DirectoryNotFoundException, UserNotFoundException, OperationFailedException {

    }

    @Override
    public void removeUserAttributes(long l, String s, String s1) throws DirectoryPermissionException, DirectoryNotFoundException, UserNotFoundException, OperationFailedException {

    }

    @Override
    public void updateUserCredential(long l, String s, PasswordCredential passwordCredential) throws DirectoryPermissionException, InvalidCredentialException, DirectoryNotFoundException, UserNotFoundException, OperationFailedException {

    }

    @Override
    public void removeUser(long l, String s) throws DirectoryNotFoundException, UserNotFoundException, DirectoryPermissionException, OperationFailedException {

    }

    @Override
    public Group findGroupByName(long l, String s) throws GroupNotFoundException, DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public GroupWithAttributes findGroupWithAttributesByName(long l, String s) throws GroupNotFoundException, DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public <T> List<T> searchGroups(long l, EntityQuery<T> entityQuery) throws DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public Group addGroup(long l, GroupTemplate groupTemplate) throws InvalidGroupException, DirectoryPermissionException, DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public Group updateGroup(long l, GroupTemplate groupTemplate) throws GroupNotFoundException, DirectoryNotFoundException, DirectoryPermissionException, InvalidGroupException, OperationFailedException, ReadOnlyGroupException {
        return null;
    }

    @Override
    public Group renameGroup(long l, String s, String s1) throws GroupNotFoundException, DirectoryNotFoundException, DirectoryPermissionException, InvalidGroupException, OperationFailedException {
        return null;
    }

    @Override
    public void storeGroupAttributes(long l, String s, Map<String, Set<String>> map) throws DirectoryPermissionException, GroupNotFoundException, DirectoryNotFoundException, OperationFailedException {

    }

    @Override
    public void removeGroupAttributes(long l, String s, String s1) throws DirectoryPermissionException, GroupNotFoundException, DirectoryNotFoundException, OperationFailedException {

    }

    @Override
    public void removeGroup(long l, String s) throws GroupNotFoundException, DirectoryNotFoundException, DirectoryPermissionException, OperationFailedException, ReadOnlyGroupException {

    }

    @Override
    public boolean isUserDirectGroupMember(long l, String s, String s1) throws DirectoryNotFoundException, OperationFailedException {
        return false;
    }

    @Override
    public boolean isGroupDirectGroupMember(long l, String s, String s1) throws DirectoryNotFoundException, OperationFailedException {
        return false;
    }

    @Override
    public void addUserToGroup(long l, String s, String s1) throws DirectoryPermissionException, DirectoryNotFoundException, UserNotFoundException, GroupNotFoundException, OperationFailedException, ReadOnlyGroupException, MembershipAlreadyExistsException {

    }

    @Override
    public void addGroupToGroup(long l, String s, String s1) throws DirectoryPermissionException, DirectoryNotFoundException, GroupNotFoundException, InvalidMembershipException, NestedGroupsNotSupportedException, OperationFailedException, ReadOnlyGroupException, MembershipAlreadyExistsException {

    }

    @Override
    public void removeUserFromGroup(long l, String s, String s1) throws DirectoryPermissionException, DirectoryNotFoundException, UserNotFoundException, GroupNotFoundException, MembershipNotFoundException, OperationFailedException, ReadOnlyGroupException {

    }

    @Override
    public void removeGroupFromGroup(long l, String s, String s1) throws DirectoryPermissionException, GroupNotFoundException, DirectoryNotFoundException, InvalidMembershipException, MembershipNotFoundException, OperationFailedException, ReadOnlyGroupException {

    }

    @Override
    public <T> List<T> searchDirectGroupRelationships(long l, MembershipQuery<T> membershipQuery) throws DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public boolean isUserNestedGroupMember(long l, String s, String s1) throws DirectoryNotFoundException, OperationFailedException {
        return false;
    }

    @Override
    public boolean isGroupNestedGroupMember(long l, String s, String s1) throws DirectoryNotFoundException, OperationFailedException {
        return false;
    }

    @Override
    public BoundedCount countDirectMembersOfGroup(long l, String s, int i) throws DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public BulkAddResult<User> addAllUsers(long l, Collection<UserTemplateWithCredentialAndAttributes> collection, boolean b) throws DirectoryPermissionException, DirectoryNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public BulkAddResult<Group> addAllGroups(long l, Collection<GroupTemplate> collection, boolean b) throws DirectoryPermissionException, DirectoryNotFoundException, OperationFailedException, InvalidGroupException {
        return null;
    }

    @Override
    public BulkAddResult<String> addAllUsersToGroup(long l, Collection<String> collection, String s) throws DirectoryPermissionException, DirectoryNotFoundException, GroupNotFoundException, OperationFailedException {
        return null;
    }

    @Override
    public boolean supportsNestedGroups(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return false;
    }

    @Override
    public boolean isSynchronisable(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return false;
    }

    @Nullable
    @Override
    public SynchronisationMode getSynchronisationMode(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return null;
    }

    @Override
    public void synchroniseCache(long l, SynchronisationMode synchronisationMode) throws OperationFailedException, DirectoryNotFoundException {

    }

    @Override
    public void synchroniseCache(long l, SynchronisationMode synchronisationMode, boolean b) throws OperationFailedException, DirectoryNotFoundException {

    }

    @Override
    public boolean isSynchronising(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return false;
    }

    @Override
    public DirectorySynchronisationInformation getDirectorySynchronisationInformation(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return null;
    }

    @Override
    public boolean supportsExpireAllPasswords(long l) throws DirectoryInstantiationException, DirectoryNotFoundException {
        return false;
    }

    @Override
    public void expireAllPasswords(long l) throws OperationFailedException, DirectoryNotFoundException {

    }

    @Override
    public AvatarReference getUserAvatarByName(long l, String s, int i) throws UserNotFoundException, OperationFailedException, DirectoryNotFoundException {
        return null;
    }

    @Override
    public User updateUserFromRemoteDirectory(@Nonnull User user) throws OperationFailedException, DirectoryNotFoundException, UserNotFoundException {
        return null;
    }

    @Override
    public User userAuthenticated(long l, String s) throws OperationFailedException, DirectoryNotFoundException, UserNotFoundException, InactiveAccountException {
        return null;
    }

    @Override
    public List<Application> findAuthorisedApplications(long l, List<String> list) {
        return null;
    }
}
