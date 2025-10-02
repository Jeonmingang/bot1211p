
package com.signition.legendary;

import org.bukkit.plugin.Plugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WebhookSender implements Sender {
    private final Plugin plugin;
    private final String webhookUrl;
    private final boolean mentionEveryone;
    private final String mentionRoleId;
    private final HttpClient http;

    public WebhookSender(Plugin plugin, String webhookUrl, boolean mentionEveryone, String mentionRoleId) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
        this.mentionEveryone = mentionEveryone;
        this.mentionRoleId = mentionRoleId == null ? "" : mentionRoleId;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public void send(String title, String description) {
        try {
            StringBuilder content = new StringBuilder();
            if (mentionEveryone) content.append("@everyone ");
            if (!mentionRoleId.isEmpty()) content.append("<@&").append(mentionRoleId).append("> ");

            String json = "{"
                    + "\"content\": " + toJson(content.toString().trim()) + ","
                    + "\"embeds\": [{"
                    +   "\"title\": " + toJson(title) + ","
                    +   "\"description\": " + toJson(description)
                    + "}]"
                    + "}";

            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
               .thenAccept(resp -> {
                   if (resp.statusCode() >= 300) {
                       plugin.getLogger().warning("Webhook 전송 실패: HTTP " + resp.statusCode() + " - " + resp.body());
                   }
               })
               .exceptionally(ex -> {
                   plugin.getLogger().warning("Webhook 전송 중 오류: " + ex.getMessage());
                   return null;
               });
        } catch (Exception e) {
            plugin.getLogger().warning("Webhook 전송 실패: " + e.getMessage());
        }
    }

    private static String toJson(String s) {
        if (s == null) return "\"\"";
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }
}
