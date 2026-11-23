package com.gangs.gangsgems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DiscordOrderBridge extends ListenerAdapter implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("GangGems/Discord");

    static final class NotReadyException extends IOException {
        NotReadyException() {
            super("Discord Integration has not initialized its bot yet");
        }
    }

    private final MinecraftServer server;
    private final JDA jda;
    private final String channelId;
    private final String webhookId;
    private final KoFiOrderProcessor processor;
    private final Runnable onCredit;

    @FunctionalInterface
    private interface TokenProvider {
        String get() throws IOException;
    }

    DiscordOrderBridge(MinecraftServer server, JDA jda, String channelId, String webhookId,
            KoFiOrderProcessor processor, Runnable onCredit) {
        this.server = server;
        this.jda = jda;
        this.channelId = channelId;
        this.webhookId = webhookId;
        this.processor = processor;
        this.onCredit = onCredit;
    }

    static DiscordOrderBridge start(MinecraftServer server, WalletStore wallet, boolean directWebhookEnabled,
            Runnable onCredit) throws IOException {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return start(server, wallet, directWebhookEnabled, onCredit, configDir.resolve("gangsgems-discord.json"),
            () -> verificationToken(System.getenv("GANGS_KOFI_TOKEN"), configDir.resolve("gangsgems-kofi-token.txt")));
    }

    static DiscordOrderBridge start(MinecraftServer server, WalletStore wallet, boolean directWebhookEnabled,
            Runnable onCredit, Path config, String token) throws IOException {
        return start(server, wallet, directWebhookEnabled, onCredit, config, () -> token);
    }

    private static DiscordOrderBridge start(MinecraftServer server, WalletStore wallet, boolean directWebhookEnabled,
            Runnable onCredit, Path config, TokenProvider token) throws IOException {
        if (!Files.exists(config)) return null;
        try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("enabled") || !root.get("enabled").isJsonPrimitive()
                    || !root.get("enabled").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Discord order bridge requires an enabled flag");
            }
            if (!root.get("enabled").getAsBoolean()) return null;
            if (directWebhookEnabled) {
                throw new IllegalArgumentException("Direct and Discord order receivers cannot both be enabled");
            }
            String channelId = WebhookConfiguration.string(root, "channelId");
            String webhookId = WebhookConfiguration.string(root, "webhookId");
            if (!channelId.matches("[0-9]{1,20}") || !webhookId.matches("[0-9]{1,20}")) {
                throw new IllegalArgumentException("Invalid Discord channel or webhook ID");
            }
            KoFiOrderProcessor processor = new KoFiOrderProcessor(token.get(), WebhookConfiguration.offers(root), wallet);
            if (!FabricLoader.getInstance().isModLoaded("dcintegration-fabric")) {
                throw new IllegalStateException("Discord Integration is not installed");
            }
            DiscordIntegration integration = DiscordIntegration.INSTANCE;
            JDA jda = integration == null ? null : integration.getJDA();
            if (jda == null) throw new NotReadyException();
            if (!jda.getGatewayIntents().contains(GatewayIntent.MESSAGE_CONTENT)) {
                throw new IllegalStateException("Discord bot is missing the Message Content intent");
            }
            DiscordOrderBridge bridge = new DiscordOrderBridge(server, jda, channelId, webhookId, processor, onCredit);
            jda.addEventListener(bridge);
            LOGGER.info("Gang Gems Discord order listener enabled.");
            return bridge;
        } catch (com.google.gson.JsonParseException | IllegalStateException failure) {
            throw new IOException("Invalid Discord order bridge configuration or Discord is not ready", failure);
        }
    }

    static String verificationToken(String environmentToken, Path file) throws IOException {
        if (environmentToken != null) {
            if (environmentToken.isBlank()) throw new IOException("Gang Gems verification token is empty");
            return environmentToken;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 4096) {
            throw new IOException("Gang Gems verification token file is missing or invalid");
        }
        String token = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new IOException("Gang Gems verification token file is empty or invalid");
        }
        return token;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!DiscordOrderMessage.matches(event.isWebhookMessage(), event.getChannel().getId(),
                event.getAuthor().getId(), channelId, webhookId)) return;
        String content = DiscordOrderMessage.jsonContent(event.getMessage().getContentRaw());
        if (content == null) {
            LOGGER.warn("Discord order message has an unsupported format; review the order manually.");
            return;
        }
        server.execute(() -> process(content));
    }

    private void process(String content) {
        try {
            String form = "data=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
            KoFiOrderProcessor.Result result = processor.process(form, System.currentTimeMillis(), this::resolvePlayer);
            if (result == KoFiOrderProcessor.Result.PENDING) {
                LOGGER.warn("A Ko-fi order needs manual player assignment.");
            } else if (result == KoFiOrderProcessor.Result.APPLIED) {
                onCredit.run();
            }
        } catch (KoFiOrderProcessor.InvalidRequest failure) {
            LOGGER.warn("Rejected a Discord order: {}", failure.getMessage());
        } catch (IOException | IllegalStateException | ArithmeticException failure) {
            LOGGER.error("Could not save or reconcile a Discord order; review it manually.", failure);
        }
    }

    private UUID resolvePlayer(String username) {
        var online = server.getPlayerManager().getPlayer(username);
        if (online != null) return online.getUuid();
        try {
            return KnownPlayerLookup.find(server.getRunDirectory().toPath().resolve("usercache.json"), username, Instant.now());
        } catch (IOException failure) {
            LOGGER.error("Could not read the player cache; the order needs manual assignment.", failure);
            return null;
        }
    }

    @Override
    public void close() {
        jda.removeEventListener(this);
    }

    String status() {
        return jda.getStatus().name();
    }
}
