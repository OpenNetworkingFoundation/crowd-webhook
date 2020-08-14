package org.opennetworking.crowd.api;

public interface OnfEventPoster {
    void send(WebhookEvent event);
}
