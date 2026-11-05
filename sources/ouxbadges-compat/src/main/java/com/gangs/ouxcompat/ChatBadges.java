package com.gangs.ouxcompat;

import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** Puts the sender's Oux badge image in front of their name in player chat: {@code <[badge] Name> message}. */
public final class ChatBadges {
    private static final Set<String> CHAT_KEYS = Set.of("chat.type.text", "chat.type.announcement", "chat.type.team.text");

    private ChatBadges() {
    }

    public static Text decorate(Text message, MinecraftClient client) {
        if (!(message.getContent() instanceof TranslatableTextContent content) || !CHAT_KEYS.contains(content.getKey())) {
            return message;
        }
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if (network == null || OuxBadgeKeys.hasBadge(message.getString())) return message;
        Object[] args = content.getArgs().clone();
        for (PlayerListEntry entry : network.getPlayerList()) {
            Team team = entry.getScoreboardTeam();
            int badge = OuxBadgeKeys.index(team == null ? null : team.getName());
            if (badge < 0) continue;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Text sender && isSender(sender, entry)) {
                    MutableText display = OuxBadgeKeys.glyphText(badge).copy();
                    display.append(Text.literal(" ").setStyle(Style.EMPTY.withFont(Style.DEFAULT_FONT_ID)));
                    // Children inherit the badge font unless they name the default font, which would turn the name into boxes.
                    String name = OuxBadgeKeys.withoutLabelPrefix(sender.getString(), badge);
                    display.append(Text.literal(name).setStyle(sender.getStyle().withFont(Style.DEFAULT_FONT_ID)));
                    args[i] = display;
                    MutableText result = Text.translatable(content.getKey(), args).setStyle(message.getStyle());
                    message.getSiblings().forEach(sibling -> result.append(sibling.copy()));
                    return result;
                }
            }
        }
        return message;
    }

    private static boolean isSender(Text sender, PlayerListEntry entry) {
        Text displayName = entry.getDisplayName();
        return OuxBadgeKeys.matchesSenderName(sender.getString(), entry.getProfile().getName(),
            displayName == null ? null : displayName.getString());
    }
}
