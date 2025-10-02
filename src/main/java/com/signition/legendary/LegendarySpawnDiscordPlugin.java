
package com.signition.legendary;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendarySpawnDiscordPlugin extends JavaPlugin {

    private Sender sender;
    private PixelmonLegendaryHook forgeHook;
    private LogAppenderHook logHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();

        String webhook = cfg.getString("discord.webhook_url", "");
        if (webhook != null && !webhook.isBlank()) {
            this.sender = new WebhookSender(this, webhook,
                    cfg.getBoolean("discord.mention_everyone", false),
                    cfg.getString("discord.mention_role_id", ""));
            getLogger().info("Discord Webhook 모드 활성화.");
        } else {
            String token = cfg.getString("discord.token", "");
            String channelId = cfg.getString("discord.channel_id", "");
            if (token == null || token.isBlank() || channelId == null || channelId.isBlank()) {
                getLogger().severe("discord.webhook_url 또는 (token + channel_id) 를 설정하세요. 알림 비활성화됨.");
            } else {
                try {
                    this.sender = new JDASender(this, token, channelId,
                            cfg.getBoolean("discord.mention_everyone", false),
                            cfg.getString("discord.mention_role_id", ""));
                    getLogger().info("Discord JDA 모드 활성화.");
                } catch (Throwable e) {
                    getLogger().severe("JDA 초기화 실패: " + e.getMessage());
                }
            }
        }

        forgeHook = new PixelmonLegendaryHook(this);
        boolean registered = forgeHook.register();
        if (registered) {
            getLogger().info("Pixelmon Legendary 이벤트 리스너 등록 완료(NeoForge EVENT_BUS).");
        } else {
            getLogger().warning("NeoForge EVENT_BUS 등록 실패 또는 Pixelmon 미탑재. 로그 패턴 감시로 폴백합니다.");
        }

        if (getConfig().getBoolean("fallback_log_matcher.enabled", true)) {
            logHook = new LogAppenderHook(this);
            logHook.register();
            getLogger().info("Pixelmon 로그 감시 리스너 활성화.");
        }
    }

    @Override
    public void onDisable() {
        if (logHook != null) logHook.unregister();
        if (forgeHook != null) forgeHook.unregister();
        if (sender instanceof JDASender j) j.shutdown();
    }

    public Sender sender() { return sender; }
}
