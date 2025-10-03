package gg.legendalert.bridge;

import gg.legendalert.LegendAlertPlugin;
import gg.legendalert.util.DiscordWebhook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatBridgeListener implements Listener {
    private final LegendAlertPlugin plugin;

    public ChatBridgeListener(LegendAlertPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.isEnabled()) return;
        String fmt = plugin.getChatFormatDiscord()
                .replace("{player}", e.getPlayer().getName())
                .replace("{message}", e.getMessage());
        DiscordWebhook.send(plugin.getWebhookUrl(), fmt);
    }
}
