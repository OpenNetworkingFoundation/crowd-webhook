package org.opennetworking.crowd.api;

import com.google.common.base.MoreObjects;

import static com.google.common.base.Objects.equal;

public class WebhookEvent {
    public EventType type;
    public WebhookUser user;
    public String groupName;
    public String oldGithubId;
    public String newGithubId;
    public String oldEmail;
    public String newEmail;

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
