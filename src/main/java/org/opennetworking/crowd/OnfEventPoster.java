package org.opennetworking.crowd;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

public class OnfEventPoster {
    private final static Logger logger = LoggerFactory.getLogger(OnfEventPoster.class);

    public static void send(OnfEventListener.WebhookEvent event) {
        Gson gson = new Gson();
        String payload = gson.toJson(event);

        HttpURLConnection connection = null;

        // TODO perhaps use Crowd configuration for this instead
        String targetUrl = "http://localhost:5000";
        String envUrl = System.getenv("ONF_WEBHOOK_URL");
        if (!Strings.isNullOrEmpty(envUrl)) {
            targetUrl = envUrl;
        }
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", Integer.toString(payload.getBytes().length));
            // TODO add some type of signature to prevent webhook spoofing
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(payload);
            wr.close();
            int responseCode = connection.getResponseCode();
            // TODO consider dropping this log message to debug
            logger.info("ONF Webhook event to {} (response {}): {}", targetUrl, responseCode, payload);
        } catch (ConnectException e) {
            logger.warn("ONF Webhook event failed to post to {} - {}: {}",
                        targetUrl, e.getMessage(), payload);
        } catch (Exception e) {
            logger.error("ONF Webhook exception", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
