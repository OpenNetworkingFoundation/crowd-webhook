package org.opennetworking.crowd.poster;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.HmacUtils;
import org.opennetworking.crowd.api.OnfEventPoster;
import org.opennetworking.crowd.api.WebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.DataOutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.google.common.base.Strings.isNullOrEmpty;

@ExportAsService({OnfEventPoster.class})
@Named("onfEventPoster")
//@Component("onfEventPoster")
public class OnfEventPosterImpl implements OnfEventPoster {
    private final static Logger logger = LoggerFactory.getLogger(OnfEventPosterImpl.class);
    private final static String DEFAULT_TARGET_URL = "http://localhost:5000";

    private String targetUrl;
    private String webhookSecret;

    // https://developer.atlassian.com/server/framework/atlassian-sdk/store-and-retrieve-plugin-data/
    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public OnfEventPosterImpl(final PluginSettingsFactory pluginSettingsFactory) {
        this(); //FIXME replace this with reading properties from Crowd
//         PluginSettings globalSettings = this.pluginSettingsFactory.createGlobalSettings();
        // globalSettings.put("bocon.send", "send");
    }

    private OnfEventPosterImpl() {
        // TODO perhaps use Crowd configuration for target url and secret instead
        this(System.getenv("ONF_WEBHOOK_URL"), System.getenv("ONF_WEBHOOK_SECRET"));
    }

    private OnfEventPosterImpl(String targetUrl, String webhookSecret) {
        pluginSettingsFactory = null;

        this.targetUrl = isNullOrEmpty(targetUrl) ? DEFAULT_TARGET_URL : targetUrl;
        this.webhookSecret = isNullOrEmpty(webhookSecret) ? null : webhookSecret;
        if (webhookSecret == null) {
            logger.warn("No webhook secret is set. Webhooks will be unsigned.");
        }
    }

    public void send(WebhookEvent event) {
        Gson gson = new Gson();
        String payload = gson.toJson(event);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", Integer.toString(payload.getBytes().length));
            if (!isNullOrEmpty(webhookSecret)) {
                // Add HTTP property with payload signature to prevent webhook spoofing
                connection.setRequestProperty("Crowd-Webhook-Signature", HmacUtils.hmacSha256Hex("key", payload));
            }
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
