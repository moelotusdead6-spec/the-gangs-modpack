package com.gangs.gangsgems;

final class DiscordOrderMessage {
    private DiscordOrderMessage() {}

    static boolean matches(boolean webhook, String channel, String author, String expectedChannel, String expectedWebhook) {
        return webhook && expectedChannel.equals(channel) && expectedWebhook.equals(author);
    }

    static String jsonContent(String content) {
        if (content == null || content.length() > 65536) return null;
        String value = content.trim();
        int start = value.indexOf("```json");
        if (start >= 0) {
            int newline = value.indexOf('\n', start);
            if (newline < 0 || !value.substring(start + 7, newline).trim().isEmpty()) return null;
            int end = value.indexOf("```", newline + 1);
            if (end < 0 || value.indexOf("```", end + 3) >= 0) return null;
            value = value.substring(newline + 1, end).trim();
        }
        return value.startsWith("{") && value.endsWith("}") ? value : null;
    }
}
