
package com.signition.legendary;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.awt.*;
import java.time.Instant;
import java.util.EnumSet;

public class JDASender implements Sender {
    private final String channelId;
    private final boolean mentionEveryone;
    private final String mentionRoleId;
    private JDA jda;

    public JDASender(org.bukkit.plugin.Plugin plugin, String token, String channelId, boolean mentionEveryone, String mentionRoleId) throws Exception {
        this.channelId = channelId;
        this.mentionEveryone = mentionEveryone;
        this.mentionRoleId = mentionRoleId == null ? "" : mentionRoleId;

        this.jda = JDABuilder.create(token, EnumSet.of(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.MESSAGE_CONTENT))
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setEnableShutdownHook(false)
                .build();

        try {
            // 일부 클래스로더에서 종료 시 CNFE 방지
            Class.forName("net.dv8tion.jda.api.events.session.ShutdownEvent", false, jda.getClass().getClassLoader());
        } catch (Throwable ignored) {}
    }

    public void shutdown() { if (jda != null) jda.shutdownNow(); }

    @Override
    public void send(String title, String description) {
        if (jda == null) return;
        MessageChannel ch = jda.getChannelById(MessageChannel.class, channelId);
        if (ch == null) return;

        StringBuilder content = new StringBuilder();
        if (mentionEveryone) content.append("@everyone ");
        if (!mentionRoleId.isEmpty()) content.append("<@&").append(mentionRoleId).append("> ");

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(new Color(0x00D1B2))
                .setTimestamp(Instant.now());

        String text = content.toString().trim();
        if (!text.isEmpty()) ch.sendMessage(text).queue();
        ch.sendMessageEmbeds(eb.build()).queue();
    }
}
