package gg.legendalert;

import gg.legendalert.bridge.ChatBridgeListener;
import gg.legendalert.log.LegendaryLogWatcher;
import gg.legendalert.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class LegendAlertPlugin extends JavaPlugin {

    private LegendaryLogWatcher watcher;
    private ChatBridgeListener chatBridgeListener;
    private String webhookUrl;
    private boolean enableLegendary;
    private boolean enableChatBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        watcher = new LegendaryLogWatcher(this);
        watcher.start();

        if (enableChatBridge) {
            chatBridgeListener = new ChatBridgeListener(this);
            Bukkit.getPluginManager().registerEvents(chatBridgeListener, this);
        }

        getLogger().info(Text.color("&aLegendAlert v1.0.1 enabled."));
    }

    @Override
    public void onDisable() {
        if (watcher != null) watcher.stop();
        getLogger().info(Text.color("&cLegendAlert disabled."));
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();
        webhookUrl = cfg.getString("webhook_url", "");
        enableLegendary = cfg.getBoolean("enable_legendary_alert", true);
        enableChatBridge = cfg.getBoolean("enable_chat_bridge", true);
    }

    public void reload() {
        reloadConfig();
        loadConfig();
    }

    public String getWebhookUrl() { return webhookUrl; }
    public boolean isLegendaryEnabled() { return enableLegendary; }
    public String getDiscordLegendaryFormat() {
        return getConfig().getString("legendary_format_discord", ":zap: **{pokemon}** spawned in **{biome}** at **{location}**");
    }
    public String getIngameLegendaryFormat() {
        return getConfig().getString("legendary_format_ingame", "&6[Legendary] &e{pokemon} &7spawned in &b{biome}&7 at &f{location}");
    }
    public String getChatFormatDiscord() {
        return getConfig().getString("chat_format_discord", "[MC] {player}: {message}");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("legendalert")) return false;
        if (!sender.hasPermission("legendalert.admin")) {
            sender.sendMessage(Text.color("&c권한이 없습니다."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Text.color("&e/legendalert reload &7- 리로드"));
            sender.sendMessage(Text.color("&e/legendalert test <포켓몬> &7- 테스트 전설 알림"));
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            reload();
            sender.sendMessage(Text.color("&a설정이 리로드되었습니다."));
            return true;
        }
        if ("test".equalsIgnoreCase(args[0])) {
            String name = args.length >= 2 ? String.join(" ", args).substring(5) : "Tapu Bulu";
            String biome = getConfig().getConfigurationSection("defaults").getString("biome", "Unknown");
            String loc = getConfig().getConfigurationSection("defaults").getString("location", "Unknown");
            watcher.notifyLegendary(name, biome, loc);
            sender.sendMessage(Text.color("&a디스코드로 테스트 전송: &f" + name));
            return true;
        }
        return false;
    }
}
