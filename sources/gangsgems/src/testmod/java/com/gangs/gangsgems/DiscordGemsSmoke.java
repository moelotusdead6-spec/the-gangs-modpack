package com.gangs.gangsgems;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

final class DiscordGemsSmoke {
    private DiscordGemsSmoke() {}

    static void run(MinecraftServer server) throws IOException {
        require(FabricLoader.getInstance().isModLoaded("dcintegration-fabric"), "Pinned Discord mod was not loaded");
        UUID recipient = UUID.randomUUID();
        String username = "G" + recipient.toString().replace("-", "").substring(0, 12);
        server.getUserCache().add(new GameProfile(recipient, username));
        require(recipient.equals(KnownPlayerLookup.find(server.getRunDirectory().toPath().resolve("usercache.json"),
            username, Instant.now())), "Offline recipient was not cached");

        WalletStore wallet = GangGems.wallet();
        KoFiOrderProcessor processor = new KoFiOrderProcessor("test-only-token", Map.of(
            new KoFiOrderProcessor.Product("sku", "bundle"), new KoFiOrderProcessor.Offer(200, 100)), wallet);
        AtomicInteger notifications = new AtomicInteger();
        JDA jda = stub(JDA.class, Map.of());
        DiscordOrderBridge bridge = new DiscordOrderBridge(server, jda, "111", "222", processor,
            notifications::incrementAndGet);
        String transaction = "smoke-discord-" + recipient;
        String order = "{\"verification_token\":\"test-only-token\",\"type\":\"Shop Order\",\"currency\":\"USD\","
            + "\"kofi_transaction_id\":\"" + transaction + "\",\"amount\":\"2.00\",\"message\":\"" + username + "\","
            + "\"shop_items\":[{\"direct_link_code\":\"sku\",\"variation_name\":\"bundle\",\"quantity\":1}]}";
        MessageChannelUnion channel = stub(MessageChannelUnion.class, Map.of("getId", "111"));
        User author = stub(User.class, Map.of("getId", "222"));
        Message message = stub(Message.class, Map.of("getIdLong", 1L, "getChannel", channel, "getAuthor", author,
            "isWebhookMessage", true, "getContentRaw", "```json\n" + order + "\n```"));
        MessageReceivedEvent event = new MessageReceivedEvent(jda, 1, message);
        bridge.onMessageReceived(event);
        bridge.onMessageReceived(event);
        require(wallet.balance(recipient) == 100, "Discord order was not credited exactly once");
        require(notifications.get() == 1, "Discord order notification callback count was incorrect");
        var path = server.getSavePath(WorldSavePath.ROOT).resolve("gangsgems").resolve("wallet.json");
        WalletStore reloaded = new WalletStore(path);
        require(reloaded.balance(recipient) == 100, "Discord order was not persisted");
        require(reloaded.unannounced(recipient) == 100, "Offline notification was not persisted");
        FixturePlayer player = new FixturePlayer(server, recipient, username);
        GangGems.notifyPlayer(player);
        require(player.messages.size() == 1 && player.messages.get(0).contains("Thank you for your support!")
            && player.messages.get(0).contains("100 Gang Gems"), "Offline thank-you message was not delivered");
        require(new WalletStore(path).unannounced(recipient) == 0, "Thank-you receipt was not persisted");
        System.out.println("GANGS_GEMS_DISCORD_SMOKE_PASS");
    }

    private static final class FixturePlayer extends ServerPlayerEntity {
        private final ArrayList<String> messages = new ArrayList<>();

        FixturePlayer(MinecraftServer server, UUID recipient, String username) {
            super(server, server.getOverworld(), new GameProfile(recipient, username));
        }

        @Override public void sendMessage(Text message, boolean overlay) {
            messages.add(message.getString());
        }
    }

    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (!values.containsKey(method.getName())) {
                throw new IllegalStateException("Unexpected test stub call: " + method.getName());
            }
            return values.get(method.getName());
        }));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
