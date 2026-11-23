package com.gangs.gangsgems;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiscordOrderBridgeTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void verificationTokenUsesEnvironmentOrPrivateFile() throws Exception {
        var file = folder.getRoot().toPath().resolve("token.txt");
        assertThrows(java.io.IOException.class, () -> DiscordOrderBridge.verificationToken(null, file));
        Files.writeString(file, "file-token\n");
        assertEquals("file-token", DiscordOrderBridge.verificationToken(null, file));
        assertEquals("environment-token", DiscordOrderBridge.verificationToken("environment-token", file));
        assertThrows(java.io.IOException.class, () -> DiscordOrderBridge.verificationToken(" ", file));
        Files.writeString(file, "bad token\n");
        assertThrows(java.io.IOException.class, () -> DiscordOrderBridge.verificationToken(null, file));
        Files.writeString(file, "x".repeat(4097));
        assertThrows(java.io.IOException.class, () -> DiscordOrderBridge.verificationToken(null, file));
    }

    @Test
    public void disabledListenerDoesNotRequireTokenFile() throws Exception {
        var root = folder.getRoot().toPath();
        Files.writeString(root.resolve("gangsgems-discord.json"), "{\"enabled\":false}");
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(root);
        try (var fabric = mockStatic(FabricLoader.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            assertNull(DiscordOrderBridge.start(mock(MinecraftServer.class),
                new WalletStore(root.resolve("wallet.json")), false, () -> {}));
        }
    }

    @Test
    public void attachesToInstalledFabricBridge() throws Exception {
        var root = folder.getRoot().toPath();
        var config = root.resolve("gangsgems-discord.json");
        Files.writeString(config, "{\"enabled\":true,\"channelId\":\"111\",\"webhookId\":\"222\","
            + "\"offers\":[{\"code\":\"sku\",\"variation\":null,\"cents\":200,\"gems\":100}]}");
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.isModLoaded("dcintegration-fabric")).thenReturn(true);
        JDA jda = mock(JDA.class);
        when(jda.getGatewayIntents()).thenReturn(EnumSet.of(GatewayIntent.MESSAGE_CONTENT));
        DiscordIntegration integration = mock(DiscordIntegration.class);
        when(integration.getJDA()).thenReturn(jda);
        DiscordIntegration previous = DiscordIntegration.INSTANCE;
        try (var fabric = mockStatic(FabricLoader.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            DiscordIntegration.INSTANCE = null;
            assertThrows(DiscordOrderBridge.NotReadyException.class, () ->
                DiscordOrderBridge.start(mock(MinecraftServer.class), new WalletStore(root.resolve("wallet.json")),
                    false, () -> {}, config, "test-only-token"));
            DiscordIntegration.INSTANCE = integration;
            DiscordOrderBridge bridge = DiscordOrderBridge.start(mock(MinecraftServer.class),
                new WalletStore(root.resolve("wallet.json")), false, () -> {}, config, "test-only-token");
            verify(jda).addEventListener(bridge);
            bridge.close();
            verify(jda).removeEventListener(bridge);
        } finally {
            DiscordIntegration.INSTANCE = previous;
        }
    }

    @Test
    public void verifiedWebhookCreditsCachedPlayerOnceAndLeavesUnknownPlayersPending() throws Exception {
        var root = folder.getRoot().toPath();
        UUID player = UUID.randomUUID();
        Files.writeString(root.resolve("usercache.json"), "[{\"name\":\"GiftPlayer\",\"uuid\":\"" + player
            + "\",\"expiresOn\":\"2099-12-01 12:00:00 +0000\"}]");
        WalletStore wallet = new WalletStore(root.resolve("wallet.json"));
        KoFiOrderProcessor processor = new KoFiOrderProcessor("test-only-token", Map.of(
            new KoFiOrderProcessor.Product("sku", null), new KoFiOrderProcessor.Offer(200, 100)), wallet);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getRunDirectory()).thenReturn(folder.getRoot());
        when(server.getPlayerManager()).thenReturn(mock(PlayerManager.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(server).execute(any(Runnable.class));
        JDA jda = mock(JDA.class);
        AtomicInteger notifications = new AtomicInteger();
        DiscordOrderBridge bridge = new DiscordOrderBridge(server, jda, "111", "222", processor,
            notifications::incrementAndGet);

        bridge.onMessageReceived(event("111", "222", true, order("one", "GiftPlayer", "test-only-token")));
        bridge.onMessageReceived(event("111", "222", true, order("one", "GiftPlayer", "test-only-token")));
        assertEquals(100, new WalletStore(root.resolve("wallet.json")).balance(player));
        assertEquals(1, notifications.get());

        bridge.onMessageReceived(event("111", "222", true, order("two", "UnknownPlayer", "test-only-token")));
        assertEquals(Long.valueOf(100), new WalletStore(root.resolve("wallet.json")).pendingPayments().get("two"));
        bridge.onMessageReceived(event("111", "222", true, order("three", "GiftPlayer", "invalid-token")));
        bridge.onMessageReceived(event("111", "999", true, order("four", "GiftPlayer", "test-only-token")));
        bridge.onMessageReceived(event("999", "222", true, order("five", "GiftPlayer", "test-only-token")));
        bridge.onMessageReceived(event("111", "222", false, order("six", "GiftPlayer", "test-only-token")));
        assertEquals(100, new WalletStore(root.resolve("wallet.json")).balance(player));
        assertEquals(1, new WalletStore(root.resolve("wallet.json")).pendingPayments().size());

        bridge.close();
        verify(jda).removeEventListener(bridge);
    }

    private static MessageReceivedEvent event(String channelId, String authorId, boolean webhook, String content) {
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        User author = mock(User.class);
        Message message = mock(Message.class);
        when(channel.getId()).thenReturn(channelId);
        when(author.getId()).thenReturn(authorId);
        when(message.getChannel()).thenReturn(channel);
        when(message.getAuthor()).thenReturn(author);
        when(message.isWebhookMessage()).thenReturn(webhook);
        when(message.getContentRaw()).thenReturn("```json\n" + content + "\n```");
        return new MessageReceivedEvent(mock(JDA.class), 1L, message);
    }

    private static String order(String transaction, String username, String token) {
        return "{\"verification_token\":\"" + token + "\",\"type\":\"Shop Order\",\"currency\":\"USD\","
            + "\"kofi_transaction_id\":\"" + transaction + "\",\"amount\":\"2.00\",\"message\":\"" + username + "\","
            + "\"shop_items\":[{\"direct_link_code\":\"sku\",\"quantity\":1}]}";
    }
}
