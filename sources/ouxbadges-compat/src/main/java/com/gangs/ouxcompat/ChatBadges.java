package com.gangs.ouxcompat;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

/**
 * Shows the sender's Oux badge image in chat. Player chat carries the sender's UUID, so the badge comes from their
 * scoreboard team whatever format the server uses (vanilla, Styled Chat's generic {@code %s} wrapper, nicknames).
 */
public final class ChatBadges {
    private static UUID pendingSender;

    private ChatBadges() {
    }

    public static void beginPlayerMessage(UUID sender) {
        pendingSender = sender;
    }

    public static void endPlayerMessage() {
        pendingSender = null;
    }

    public static Text decorate(Text message, MinecraftClient client) {
        UUID sender = pendingSender;
        pendingSender = null;
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if (network == null) return message;
        if (sender != null) {
            PlayerListEntry entry = network.getPlayerListEntry(sender);
            int badge = entry == null ? -1 : badge(entry);
            return badge < 0 ? message : BadgeText.apply(message, badge, names(entry), false);
        }
        // Messages without a sender (system/profileless): only swap an explicit "[Label]" in front of a badge holder's name.
        String plain = message.getString();
        for (PlayerListEntry entry : network.getPlayerList()) {
            int badge = badge(entry);
            if (badge < 0) continue;
            String label = "[" + OuxBadgeKeys.label(badge) + "] ";
            for (String name : names(entry)) {
                if (name != null && !name.isEmpty() && plain.contains(label + name)) {
                    return BadgeText.apply(message, badge, List.of(), true);
                }
            }
        }
        return message;
    }

    public static Text decorateTabName(Text name, PlayerListEntry entry) {
        int badge = badge(entry);
        return badge < 0 ? name : BadgeText.apply(name, badge, List.of(), false, true);
    }

    static int badge(PlayerListEntry entry) {
        Team team = entry.getScoreboardTeam();
        return OuxBadgeKeys.index(team == null ? null : team.getName());
    }

    static List<String> names(PlayerListEntry entry) {
        Text display = entry.getDisplayName();
        String profile = entry.getProfile().getName();
        return display == null ? List.of(profile) : List.of(display.getString(), profile);
    }
}
