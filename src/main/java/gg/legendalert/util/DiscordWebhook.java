package gg.legendalert.util;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    public static void send(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String json = "{\"content\":" + quote(content) + "}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(out);
            int code = conn.getResponseCode();
            try { if (conn.getInputStream()!=null) conn.getInputStream().close(); } catch (Exception ignored){}
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String quote(String s) {
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n","\\n");
        return "\"" + escaped + "\"";
    }
}
