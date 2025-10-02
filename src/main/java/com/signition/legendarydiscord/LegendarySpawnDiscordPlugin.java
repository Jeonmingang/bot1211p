
package com.signition.legendarydiscord;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class LegendarySpawnDiscordPlugin extends JavaPlugin {

    private String webhookUrl;
    private String roleId;
    private boolean debug;
    private int dedupeSeconds;

    private Pattern legendaryPattern;
    private Pattern coordsPattern;

    private final AtomicReference<PendingSpawn> pending = new AtomicReference<>(null);
    private final Map<String, Long> dedupe = new ConcurrentHashMap<>();

    private LoggerContext ctx;
    private Logger root;
    private AbstractAppender appender;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        this.webhookUrl = getConfig().getString("webhook_url", "").trim();
        this.roleId = getConfig().getString("role_mention_id", "").trim();
        this.debug = getConfig().getBoolean("debug", false);
        this.dedupeSeconds = getConfig().getInt("dedupe_seconds", 60);

        String legendaryRegex = getConfig().getString("regex.legendary_line");
        String coordsRegex = getConfig().getString("regex.coords_line");
        this.legendaryPattern = Pattern.compile(legendaryRegex);
        this.coordsPattern = Pattern.compile(coordsRegex);

        if (webhookUrl.isEmpty()) {
            getLogger().severe("[LegendarySpawnDiscord] webhook_url이 비어있습니다. config.yml을 설정하고 서버를 재시작하세요.");
            // keep plugin enabled so they can /reload config; but no sending.
        } else {
            getLogger().info("[LegendarySpawnDiscord] Discord Webhook 모드 활성화.");
        }

        hookLog4j();
        getLogger().info("[LegendarySpawnDiscord] Pixelmon 전설 스폰 감시를 시작합니다 (Log4j2 Appender).");
    }

    @Override
    public void onDisable() {
        unhookLog4j();
        getLogger().info("[LegendarySpawnDiscord] 비활성화 완료.");
    }

    private void hookLog4j() {
        try {
            this.ctx = (LoggerContext) LogManager.getContext(false);
            this.root = ctx.getRootLogger();

            this.appender = new AbstractAppender("LegendarySpawnWatcher", null,
                    PatternLayout.newBuilder().withPattern("%m").withCharset(StandardCharsets.UTF_8).build(),
                    false, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    String msg = event.getMessage().getFormattedMessage();
                    onLogLine(msg);
                }
            };
            this.appender.start();
            this.root.addAppender(this.appender);
        } catch (Throwable t) {
            getLogger().severe("[LegendarySpawnDiscord] Log4j2 Appender 초기화 실패: " + t);
        }
    }

    private void unhookLog4j() {
        try {
            if (root != null && appender != null) {
                root.removeAppender(appender);
                appender.stop();
            }
        } catch (Throwable t) {
            getLogger().severe("[LegendarySpawnDiscord] Log4j2 Appender 제거 실패: " + t);
        }
    }

    private void onLogLine(String line) {
        if (line == null) return;
        if (debug) getLogger().info("[LegendarySpawnDiscord:DEBUG] " + line);

        Matcher mLegend = legendaryPattern.matcher(line);
        if (mLegend.find()) {
            String name = mLegend.group("name").trim();
            String biome = mLegend.group("biome").trim();
            pending.set(new PendingSpawn(name, biome, System.currentTimeMillis()));
            if (debug) getLogger().info("[LegendarySpawnDiscord] 감지: " + name + " in " + biome);
            return;
        }

        Matcher mCoords = coordsPattern.matcher(line);
        if (mCoords.find()) {
            String name = mCoords.group("name").trim();
            String x = mCoords.group("x");
            String y = mCoords.group("y");
            String z = mCoords.group("z");

            PendingSpawn p = pending.getAndSet(null);
            if (p != null && p.name.equalsIgnoreCase(name) && (System.currentTimeMillis() - p.timeMs) < 10000) {
                String key = name + "@" + x + "," + y + "," + z;
                long now = System.currentTimeMillis();
                long last = dedupe.getOrDefault(key, 0L);
                if ((now - last) / 1000 < dedupeSeconds) {
                    if (debug) getLogger().info("[LegendarySpawnDiscord] 중복 감지 무시: " + key);
                    return;
                }
                dedupe.put(key, now);
                sendWebhook(name, p.biome, x, y, z);
            }
        }
    }

    private void sendWebhook(String name, String biome, String x, String y, String z) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            getLogger().warning("[LegendarySpawnDiscord] webhook_url이 설정되지 않아 디스코드로 전송하지 않습니다.");
            return;
        }
        String title = getConfig().getString("message.title", "⭐ Legendary Spawn!");
        String content = getConfig().getString("message.content", "{name} spawned in {biome} at X:{x} Y:{y} Z:{z}")
                .replace("{name}", name).replace("{biome}", biome)
                .replace("{x}", x).replace("{y}", y).replace("{z}", z);
        String footer = getConfig().getString("message.footer", "Legendary notifier");

        String mention = "";
        if (roleId != null && !roleId.isEmpty()) {
            mention = "<@&" + roleId + "> ";
        }

        String json = "{"
                + "\"content\":" + quote(mention)
                + ",\"embeds\":[{"
                + "\"title\":" + quote(title)
                + ",\"description\":" + quote(content)
                + ",\"footer\":{\"text\":" + quote(footer) + "}"
                + "}]}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        getLogger().severe("[LegendarySpawnDiscord] 웹훅 전송 실패: " + err);
                    } else {
                        int code = resp.statusCode();
                        if (code / 100 != 2) {
                            getLogger().severe("[LegendarySpawnDiscord] 웹훅 상태 코드 " + code + " 응답: " + resp.body());
                        } else if (debug) {
                            getLogger().info("[LegendarySpawnDiscord] 웹훅 전송 성공 (" + code + ")");
                        }
                    }
                });
        } catch (Throwable t) {
            getLogger().severe("[LegendarySpawnDiscord] 웹훅 요청 생성 실패: " + t);
        }
    }

    private static String quote(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static class PendingSpawn {
        final String name;
        final String biome;
        final long timeMs;

        PendingSpawn(String name, String biome, long timeMs) {
            this.name = name;
            this.biome = biome;
            this.timeMs = timeMs;
        }
    }
}
