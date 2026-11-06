package com.gangs.ouxcompat;

import net.minecraft.text.Text;

final class SystemBadges {
    private static final String AFK_ENTER = " is now AFK.";
    private static final String AFK_EXIT = " is no longer AFK.";
    private static final String AFK_PREFIX = "[AFK] ";

    private SystemBadges() {
    }

    static Text decorate(Text message, int badge, String profile, String display) {
        String plain = message.getString();
        String label = "[" + OuxBadgeKeys.label(badge) + "] ";
        String suffix = plain.endsWith(AFK_ENTER) ? AFK_ENTER : plain.endsWith(AFK_EXIT) ? AFK_EXIT : null;
        if (suffix != null) {
            String subject = plain.substring(0, plain.length() - suffix.length());
            String nickname = nickname(display, label);
            if (subject.equals(label + profile) || nickname != null && subject.equals(label + nickname)) {
                return BadgeText.replaceLabelAt(message, badge, 0);
            }
            return message;
        }

        for (String name : new String[]{display, profile}) {
            if (name == null || name.isEmpty()) continue;
            int at = findName(plain, label + name);
            if (at >= 0) return BadgeText.replaceLabelAt(message, badge, at);
        }
        return message;
    }

    private static String nickname(String display, String label) {
        if (display == null) return null;
        int labelAt = display.indexOf(label);
        if (labelAt >= 0) return display.substring(labelAt + label.length());
        return display.startsWith(AFK_PREFIX) ? display.substring(AFK_PREFIX.length()) : display;
    }

    private static int findName(String plain, String candidate) {
        int from = 0;
        while (true) {
            int at = plain.indexOf(candidate, from);
            if (at < 0) return -1;
            int end = at + candidate.length();
            if (end == plain.length() || !isNameCharacter(plain.codePointAt(end))) return at;
            from = end;
        }
    }

    private static boolean isNameCharacter(int character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '~';
    }
}
