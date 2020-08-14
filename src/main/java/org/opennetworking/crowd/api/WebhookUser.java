package org.opennetworking.crowd.api;

import com.atlassian.crowd.model.user.UserWithAttributes;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static com.google.common.base.Objects.equal;

public class WebhookUser {
    public static final String EMAIL_ATTRIBUTE = "Email";
    public static final String GITHUB_ID_ATTRIBUTE = "github_id";

    public String username;
    public String email;
    public String name; // display name
    public List<String> groups;
    public String githubId;

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