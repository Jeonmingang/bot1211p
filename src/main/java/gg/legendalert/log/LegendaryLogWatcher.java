package gg.legendalert.log;

import gg.legendalert.LegendAlertPlugin;
import gg.legendalert.util.DiscordWebhook;
import gg.legendalert.util.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches console logs for Pixelmon legendary spawn lines and notifies.
 * Supports 2-line aggregation to capture biome on first line and coordinates on second line.
 */
public class LegendaryLogWatcher {

    private final LegendAlertPlugin plugin;
    private Appender appender;
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<String> legendaryNames = new ArrayList<>();

    // Small temporal aggregator to stitch two consecutive lines
    private volatile String lastPokemonFromMsg = null;
    private volatile String lastBiomeFromMsg = null;
    private volatile long lastMsgTime = 0L;
    private static final long AGGREGATE_WINDOW_MS = 4000L; // 4 seconds

    public LegendaryLogWatcher(LegendAlertPlugin plugin) {
        this.plugin = plugin;
        loadPatterns();
    }

    private void loadPatterns() {
        List<String> ptrns = plugin.getConfig().getStringList("patterns");
        for (String s : ptrns) {
            try {
                patterns.add(Pattern.compile(s));
            } catch (Exception ignored) {}
        }
        legendaryNames.addAll(plugin.getConfig().getStringList("legendary_names"));
    }

    public void start() {
        Logger root = (Logger) LogManager.getRootLogger();
        appender = new AbstractAppender("LegendAlertAppender", null, PatternLayout.createDefaultLayout(), false, null) {
            @Override
            public void append(LogEvent event) {
                handle(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        root.addAppender(appender);
    }

    public void stop() {
        if (appender != null) {
            Logger root = (Logger) LogManager.getRootLogger();
            root.removeAppender(appender);
            appender.stop();
            appender = null;
        }
    }

    private boolean containsLegendaryName(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String name : legendaryNames) {
            if (lower.contains(name.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String squash(String s) {
        return s == null ? null : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private void handle(String line) {
        if (!plugin.isLegendaryEnabled()) return;
        if (line == null) return;
        if (!line.contains("[Pixelmon]") && !containsLegendaryName(line) && !line.startsWith("Spawned")) return;

        String pokemon = null, biome = null, location = null;
        String x = null, y = null, z = null;

        for (Pattern p : patterns) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                try { if (m.group("pokemon") != null) pokemon = m.group("pokemon").trim(); } catch (Exception ignored) {}
                try { if (m.group("biome") != null) biome = m.group("biome").trim(); } catch (Exception ignored) {}
                try { if (m.group("location") != null) location = m.group("location").trim(); } catch (Exception ignored) {}
                try { if (m.group("x") != null) x = m.group("x").trim(); } catch (Exception ignored) {}
                try { if (m.group("y") != null) y = m.group("y").trim(); } catch (Exception ignored) {}
                try { if (m.group("z") != null) z = m.group("z").trim(); } catch (Exception ignored) {}
                break;
            }
        }

        long now = System.currentTimeMillis();

        // First message carrying biome
        if (pokemon != null && biome != null && (location == null && x == null && z == null)) {
            lastPokemonFromMsg = pokemon;
            lastBiomeFromMsg = biome;
            lastMsgTime = now;
            return;
        }

        // Second message carrying coords or location
        if (pokemon == null && (x != null || location != null)) {
            if (lastPokemonFromMsg != null && (now - lastMsgTime) <= AGGREGATE_WINDOW_MS) {
                pokemon = lastPokemonFromMsg;
                biome = lastBiomeFromMsg;
            }
        }

        // Fallback detection
        if (pokemon == null && containsLegendaryName(line)) {
            for (String n : legendaryNames) {
                if (line.toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))) {
                    pokemon = n;
                    break;
                }
            }
        }

        if (pokemon == null) return;

        // Build location if x/y/z
        if (location == null && (x != null || y != null || z != null)) {
            String bx = (x != null ? "x=" + x : "");
            String by = (y != null ? " y=" + y : "");
            String bz = (z != null ? " z=" + z : "");
            location = (bx + by + bz).trim();
        }

        if (biome == null) biome = plugin.getConfig().getConfigurationSection("defaults").getString("biome", "Unknown");
        if (location == null || location.isEmpty()) {
            location = plugin.getConfig().getConfigurationSection("defaults").getString("location", "Unknown");
        }

        // Clear aggregator
        if (lastPokemonFromMsg != null && squash(lastPokemonFromMsg).equals(squash(pokemon))) {
            lastPokemonFromMsg = null;
            lastBiomeFromMsg = null;
            lastMsgTime = 0L;
        }

        notifyLegendary(pokemon, biome, location);
    }

    public void notifyLegendary(String pokemon, String biome, String location) {
        String msgIn = plugin.getIngameLegendaryFormat()
                .replace("{pokemon}", pokemon)
                .replace("{biome}", biome)
                .replace("{location}", location);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(Text.color(msgIn)));

        String msgDc = plugin.getDiscordLegendaryFormat()
                .replace("{pokemon}", pokemon)
                .replace("{biome}", biome)
                .replace("{location}", location);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> DiscordWebhook.send(plugin.getWebhookUrl(), msgDc));
    }
}
