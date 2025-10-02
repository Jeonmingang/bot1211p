
package com.signition.legendary;

import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class PixelmonLegendaryHook {
    private final org.bukkit.plugin.Plugin plugin;
    private final AtomicLong lastSentAt = new AtomicLong(0);
    private Object registeredOnBus = null;

    public PixelmonLegendaryHook(org.bukkit.plugin.Plugin plugin) { this.plugin = plugin; }

    public boolean register() {
        try {
            Class.forName("com.pixelmonmod.pixelmon.api.events.spawning.LegendarySpawnEvent$DoSpawn");
        } catch (Throwable t) {
            plugin.getLogger().warning("Pixelmon LegendarySpawnEvent.DoSpawn 클래스를 찾을 수 없습니다: " + t.getMessage());
            return false;
        }
        try {
            Class<?> neoForge = Class.forName("net.neoforged.neoforge.common.NeoForge");
            Object bus = neoForge.getField("EVENT_BUS").get(null);

            Class<?> eventPriority = Class.forName("net.neoforged.bus.api.EventPriority");
            Object normal = java.lang.Enum.valueOf((Class<java.lang.Enum>) eventPriority, "NORMAL");
            Class<?> doSpawn = Class.forName("com.pixelmonmod.pixelmon.api.events.spawning.LegendarySpawnEvent$DoSpawn");
            Class<?> consumerCls = Class.forName("java.util.function.Consumer");

            Consumer<Object> handler = (evt) -> handleDoSpawn(evt);

            // 다양한 버전의 addListener 시그니처 대응
            try {
                Method addListener4 = bus.getClass().getMethod("addListener", eventPriority, boolean.class, Class.class, consumerCls);
                addListener4.invoke(bus, normal, false, doSpawn, handler);
                registeredOnBus = bus;
                return true;
            } catch (NoSuchMethodException e) {
                try {
                    Method addListener2 = bus.getClass().getMethod("addListener", Class.class, consumerCls);
                    addListener2.invoke(bus, doSpawn, handler);
                    registeredOnBus = bus;
                    return true;
                } catch (NoSuchMethodException ex) {
                    try {
                        Method register = bus.getClass().getMethod("register", Object.class);
                        register.invoke(bus, this);
                        registeredOnBus = this;
                        return true;
                    } catch (Throwable x) {
                        plugin.getLogger().warning("EVENT_BUS 등록 실패(모든 경로): " + x.getMessage());
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("NeoForge EVENT_BUS 등록 실패: " + t.getMessage());
            return false;
        }
    }

    public void unregister() {
        try {
            if (registeredOnBus == null) return;
            Object bus = Class.forName("net.neoforged.neoforge.common.NeoForge").getField("EVENT_BUS").get(null);
            try {
                Method unregister = bus.getClass().getMethod("unregister", Object.class);
                unregister.invoke(bus, registeredOnBus);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private void handleDoSpawn(Object event) {
        FileConfiguration cfg = ((org.bukkit.plugin.java.JavaPlugin)plugin).getConfig();
        int dedupeSeconds = Math.max(0, cfg.getInt("message.dedupe_seconds", 10));
        long now = Instant.now().getEpochSecond();
        long last = lastSentAt.get();
        if (dedupeSeconds > 0 && (now - last) < dedupeSeconds) return;
        lastSentAt.set(now);

        try {
            // species
            Method getLegendary = event.getClass().getMethod("getLegendary");
            Object species = getLegendary.invoke(event);
            String name = safeString(species, "getName");
            int dex = safeInt(species, "getDex");

            // action & spawnLocation
            Field actionF = event.getClass().getDeclaredField("action");
            actionF.setAccessible(true);
            Object action = actionF.get(event);

            Integer x = null, y = null, z = null;
            String biome = "-";
            String dimension = "-";
            String near = "";

            // 1) 가장 직통: action.pos (확인됨)
            try {
                Object pos = action.getClass().getField("pos").get(action);
                x = (Integer) pos.getClass().getMethod("getX").invoke(pos);
                y = (Integer) pos.getClass().getMethod("getY").invoke(pos);
                z = (Integer) pos.getClass().getMethod("getZ").invoke(pos);
            } catch (Throwable ignored) {}

            // 2) spawnLocation 경유
            Object spawnLoc = null;
            try { spawnLoc = action.getClass().getField("spawnLocation").get(action); } catch (Throwable ignored) {}

            // biome / dimension
            if (spawnLoc != null) {
                try { Object b = spawnLoc.getClass().getField("biome").get(spawnLoc); biome = Objects.toString(b); } catch (Throwable ignored) {}
                try {
                    Object loc = spawnLoc.getClass().getField("location").get(spawnLoc);
                    if (loc != null) {
                        try {
                            Object world = loc.getClass().getField("world").get(loc);
                            Object rk = world.getClass().getMethod("dimension").invoke(world);
                            dimension = Objects.toString(rk);
                        } catch (Throwable ignored) {}
                        if (x == null) {
                            try {
                                Object pos = loc.getClass().getField("pos").get(loc);
                                x = (Integer) pos.getClass().getMethod("getX").invoke(pos);
                                y = (Integer) pos.getClass().getMethod("getY").invoke(pos);
                                z = (Integer) pos.getClass().getMethod("getZ").invoke(pos);
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}

                // near player (cause.getName().getString())
                try {
                    Object cause = spawnLoc.getClass().getField("cause").get(spawnLoc);
                    if (cause != null) {
                        Object comp = cause.getClass().getMethod("getName").invoke(cause);
                        String playerName = (String) comp.getClass().getMethod("getString").invoke(comp);
                        near = " (근처: " + playerName + ")";
                    }
                } catch (Throwable ignored) {}
            }

            String template = cfg.getString("message.template", "✨ 전설 포켓몬 스폰! **{name}** (#{dex}) — 좌표 {x} {y} {z}{near}");
            template = template.replace("{name}", name)
                    .replace("{dex}", String.valueOf(dex))
                    .replace("{x}", x == null ? "?" : String.valueOf(x))
                    .replace("{y}", y == null ? "?" : String.valueOf(y))
                    .replace("{z}", z == null ? "?" : String.valueOf(z))
                    .replace("{near}", near)
                    .replace("{biome}", biome)
                    .replace("{dimension}", dimension);

            Sender s = ((LegendarySpawnDiscordPlugin) plugin).sender();
            if (s != null) s.send("전설 스폰 감지", template);
        } catch (Throwable t) {
            plugin.getLogger().warning("Legendary 스폰 처리 중 오류: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static int safeInt(Object o, String method) throws Exception {
        try { return (int) o.getClass().getMethod(method).invoke(o); }
        catch (NoSuchMethodException e) { Object v = o.getClass().getMethod(method).invoke(o); return Integer.parseInt(String.valueOf(v)); }
    }
    private static String safeString(Object o, String method) throws Exception {
        try { Object r = o.getClass().getMethod(method).invoke(o); return r == null ? "" : r.toString(); }
        catch (NoSuchMethodException e) { return String.valueOf(o); }
    }
}
