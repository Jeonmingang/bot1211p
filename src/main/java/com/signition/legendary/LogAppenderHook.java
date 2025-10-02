
package com.signition.legendary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAppenderHook {

    private final org.bukkit.plugin.Plugin plugin;
    private Logger root;
    private Appender appender;

    private Pattern legendaryPattern;
    private Pattern coordsPattern;
    private final Map<String, Long> lastByName = new ConcurrentHashMap<>();

    public LogAppenderHook(org.bukkit.plugin.Plugin plugin) { this.plugin = plugin; }

    public void register() {
        try {
            FileConfiguration cfg = ((org.bukkit.plugin.java.JavaPlugin)plugin).getConfig();
            String p1 = cfg.getString("fallback_log_matcher.legendary_line_regex",
                    "^\\[Pixelmon\\] (?P<name>[A-Za-z'\\- ]+) has spawned in a (?P<biome>.+?) biome!$");
            String p2 = cfg.getString("fallback_log_matcher.coords_line_regex",
                    "^Spawned (?P<name>[A-Za-z'\\- ]+) at: .* x:(?P<x>-?\\d+), y:(?P<y>-?\\d+), z:(?P<z>-?\\d+)$");
            legendaryPattern = Pattern.compile(p1);
            coordsPattern = Pattern.compile(p2);

            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            root = ctx.getRootLogger();
            Layout<? extends Serializable> layout = PatternLayout.newBuilder().withPattern("%m").build();
            appender = new AbstractAppender("LegendarySpawnDiscordAppender", (Filter) null, layout, false, null) {
                @Override
                public void append(LogEvent event) {
                    String m = event.getMessage().getFormattedMessage();
                    if (m == null) return;
                    try { handleLine(m); } catch (Throwable ignored) {}
                }
            };
            ((AbstractAppender) appender).start();
            root.addAppender(appender);
        } catch (Throwable t) {
            plugin.getLogger().warning("로그 감시 초기화 실패: " + t.getMessage());
        }
    }

    public void unregister() {
        try {
            if (root != null && appender != null) root.removeAppender(appender);
            if (appender instanceof AbstractAppender a) a.stop();
        } catch (Throwable ignored) {}
    }

    private static class Tmp {
        String name; String biome; Integer x; Integer y; Integer z;
    }

    private final Map<String, Tmp> tmpBySpecies = new ConcurrentHashMap<>();

    private void handleLine(String line) {
        if (legendaryPattern != null) {
            Matcher m = legendaryPattern.matcher(line);
            if (m.find()) {
                String name = group(m, "name");
                String biome = group(m, "biome");
                Tmp t = tmpBySpecies.getOrDefault(name, new Tmp());
                t.name = name; t.biome = biome;
                tmpBySpecies.put(name, t);
            }
        }
        if (coordsPattern != null) {
            Matcher m = coordsPattern.matcher(line);
            if (m.find()) {
                String name = group(m, "name");
                int x = Integer.parseInt(group(m, "x"));
                int y = Integer.parseInt(group(m, "y"));
                int z = Integer.parseInt(group(m, "z"));

                Tmp t = tmpBySpecies.getOrDefault(name, new Tmp());
                t.name = name; t.x = x; t.y = y; t.z = z;
                tmpBySpecies.put(name, t);
                sendFromTmp(t);
            }
        }
    }

    private String group(Matcher m, String g) {
        try { return m.group(g); } catch (IllegalArgumentException e) { return m.group(); }
    }

    private void sendFromTmp(Tmp t) {
        if (t == null || t.name == null) return;
        long now = System.currentTimeMillis();
        Long last = lastByName.get(t.name);
        if (last != null && now - last < 8000) return; // 중복 억제
        lastByName.put(t.name, now);

        String biome = t.biome != null ? t.biome : "-";
        String coords = (t.x != null && t.y != null && t.z != null)
                ? (t.x + " " + t.y + " " + t.z) : "-";
        String message = "✨ 전설 포켓몬 스폰! **" + t.name + "** — 바이옴 " + biome + ", 좌표 " + coords;

        Sender s = ((LegendarySpawnDiscordPlugin) plugin).sender();
        if (s != null) s.send("전설 스폰 감지", message);
    }
}
