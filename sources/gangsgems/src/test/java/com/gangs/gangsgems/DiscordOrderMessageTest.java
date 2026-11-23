package com.gangs.gangsgems;

import static org.junit.Assert.*;

import org.junit.Test;

public class DiscordOrderMessageTest {
    @Test
    public void acceptsOnlyTheConfiguredWebhookAndChannel() {
        assertTrue(DiscordOrderMessage.matches(true, "1", "2", "1", "2"));
        assertFalse(DiscordOrderMessage.matches(false, "1", "2", "1", "2"));
        assertFalse(DiscordOrderMessage.matches(true, "3", "2", "1", "2"));
        assertFalse(DiscordOrderMessage.matches(true, "1", "3", "1", "2"));
    }

    @Test
    public void extractsPlainAndFencedJsonWithoutAcceptingIncompleteMessages() {
        assertEquals("{\"type\":\"Shop Order\"}", DiscordOrderMessage.jsonContent(
            "Order received:\n```json\r\n{\"type\":\"Shop Order\"}\r\n```"));
        assertEquals("{}", DiscordOrderMessage.jsonContent(" {} "));
        assertNull(DiscordOrderMessage.jsonContent("```json\n{}"));
        assertNull(DiscordOrderMessage.jsonContent("```json\n{}\n``` ```json\n{}\n```"));
        assertNull(DiscordOrderMessage.jsonContent("x".repeat(65537)));
    }
}
