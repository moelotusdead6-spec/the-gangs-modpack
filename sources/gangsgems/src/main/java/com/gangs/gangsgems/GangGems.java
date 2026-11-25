package com.gangs.gangsgems;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GangGems implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("GangGems");
    private static WalletStore wallet;
    private static KoFiWebhookServer webhook;
    private static DiscordOrderBridge discord;
    private static boolean discordPending;
    private static int discordRetryTicks;
    private static boolean furnitureCraftingEnabled;

    @Override
    public void onInitialize() {
        try {
            furnitureCraftingEnabled = FurnitureConfiguration.enabled(
                FabricLoader.getInstance().getConfigDir().resolve("gangsgems-furniture.json"));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Gang Gems furniture configuration", failure);
        }
        if (furnitureCraftingEnabled) {
            var mod = FabricLoader.getInstance().getModContainer("gangsgems").orElseThrow();
            if (!ResourceManagerHelper.registerBuiltinResourcePack(new Identifier("gangsgems", "gangsgems_recipes"),
                    mod, ResourcePackActivationType.ALWAYS_ENABLED)) {
                throw new IllegalStateException("Could not register Gang Gems recipe overrides");
            }
        }
        ServerLifecycleEvents.SERVER_STARTED.register(GangGems::start);
        ServerTickEvents.END_SERVER_TICK.register(GangGems::retryDiscord);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            discordPending = false;
            discordRetryTicks = 0;
            if (discord != null) discord.close();
            discord = null;
            if (webhook != null) webhook.close();
            webhook = null;
            wallet = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            notifyPlayer(handler.player);
            CraftDelivery.deliverPending(handler.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ganggems")
                .then(balanceCommand("balance"))
                .then(CommandManager.literal("link").executes(context -> link(context.getSource())))
                .then(CommandManager.literal("claim").executes(context -> {
                    CraftDelivery.deliverPending(context.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("status").requires(StaffAccess::canMonitor)
                    .executes(context -> status(context.getSource())))
                .then(CommandManager.literal("pending").requires(StaffAccess::canMonitor)
                    .executes(context -> pending(context.getSource(), 1))
                    .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> pending(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                .then(CommandManager.literal("transaction").requires(StaffAccess::canMonitor)
                    .then(CommandManager.argument("transaction", StringArgumentType.word())
                        .executes(context -> transaction(context.getSource(),
                            StringArgumentType.getString(context, "transaction")))))
                .then(CommandManager.literal("history").requires(StaffAccess::canMonitor)
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .executes(context -> history(context.getSource(),
                            GameProfileArgumentType.getProfileArgument(context, "player"), 1))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                            .executes(context -> history(context.getSource(),
                                GameProfileArgumentType.getProfileArgument(context, "player"),
                                IntegerArgumentType.getInteger(context, "page"))))))
                .then(CommandManager.literal("audit").requires(StaffAccess::canMonitor)
                    .executes(context -> audit(context.getSource(), 1))
                    .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> audit(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                .then(adjustmentCommand("grant", false))
                .then(adjustmentCommand("remove", true))
                .then(CommandManager.literal("credit").requires(StaffAccess::canMonitor)
                    .then(CommandManager.argument("transaction", StringArgumentType.word())
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .then(CommandManager.argument("gems", LongArgumentType.longArg(1))
                                .executes(context -> credit(context.getSource(),
                                    StringArgumentType.getString(context, "transaction"),
                                    GameProfileArgumentType.getProfileArgument(context, "player"),
                                    LongArgumentType.getLong(context, "gems")))))))
                .then(CommandManager.literal("assign").requires(StaffAccess::canMonitor)
                    .then(CommandManager.argument("transaction", StringArgumentType.word())
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .executes(context -> assign(context.getSource(),
                                StringArgumentType.getString(context, "transaction"),
                                GameProfileArgumentType.getProfileArgument(context, "player")))))));
            dispatcher.register(balanceCommand("gb"));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> balanceCommand(String name) {
        return CommandManager.literal(name).executes(context -> balance(context.getSource()))
            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                .requires(StaffAccess::canMonitor)
                .executes(context -> staffBalance(context.getSource(),
                    GameProfileArgumentType.getProfileArgument(context, "player"))));
    }

    static void start(MinecraftServer server) {
        try {
            Path file = server.getSavePath(WorldSavePath.ROOT).resolve("gangsgems").resolve("wallet.json");
            wallet = new WalletStore(file);
            webhook = WebhookConfiguration.start(wallet, () -> server.execute(() ->
                server.getPlayerManager().getPlayerList().forEach(GangGems::notifyPlayer)));
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.error("Gang Gems could not start; payments and wallet access are disabled.", failure);
            if (webhook != null) webhook.close();
            wallet = null;
            webhook = null;
            return;
        }
        if (FabricLoader.getInstance().isModLoaded("dcintegration-fabric")) {
            startDiscord(server);
        } else if (java.nio.file.Files.exists(FabricLoader.getInstance().getConfigDir().resolve("gangsgems-discord.json"))) {
            LOGGER.warn("Discord Integration is missing; the Gang Gems Discord bridge is unavailable.");
        }
    }

    private static void startDiscord(MinecraftServer server) {
        try {
            discord = DiscordOrderBridge.start(server, wallet, webhook != null, () ->
                server.getPlayerManager().getPlayerList().forEach(GangGems::notifyPlayer));
            discordPending = false;
        } catch (DiscordOrderBridge.NotReadyException failure) {
            if (!discordPending) LOGGER.warn("Discord bot is not ready; Gang Gems will retry.");
            discordPending = true;
        } catch (IOException | IllegalArgumentException failure) {
            discordPending = false;
            LOGGER.error("Gang Gems Discord orders are disabled; the wallet remains available.", failure);
        }
    }

    static void retryDiscord(MinecraftServer server) {
        if (!discordPending || ++discordRetryTicks < 100) return;
        discordRetryTicks = 0;
        startDiscord(server);
    }

    private static WalletStore requireWallet(ServerCommandSource source) {
        if (wallet == null) source.sendError(Text.literal("[Gang Gems] Wallet unavailable; contact staff."));
        return wallet;
    }

    static WalletStore wallet() {
        return wallet;
    }

    public static boolean furnitureCraftingEnabled() {
        return furnitureCraftingEnabled;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adjustmentCommand(
            String name, boolean remove) {
        return CommandManager.literal(name)
            .requires(remove ? StaffAccess::canRemove : StaffAccess::canMonitor)
            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                .then(CommandManager.argument("gems", LongArgumentType.longArg(1))
                    .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> adjust(context.getSource(),
                            GameProfileArgumentType.getProfileArgument(context, "player"),
                            LongArgumentType.getLong(context, "gems"), remove,
                            StringArgumentType.getString(context, "reason"))))));
    }

    private static int balance(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        player.sendMessage(Text.literal("[Gang Gems] Balance: " + store.balance(player.getUuid()) + " Gems."), false);
        return 1;
    }

    private static int link(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        try {
            String code = store.createLink(player.getUuid(), System.currentTimeMillis());
            player.sendMessage(Text.literal("[Gang Gems] Include this one-use code in your private Ko-fi order message: " + code), false);
            player.sendMessage(Text.literal("[Gang Gems] It expires in 24 hours. Never share it in public chat."), false);
            return 1;
        } catch (IOException | ArithmeticException failure) {
            LOGGER.error("Could not save a Gang Gems link code.", failure);
            source.sendError(Text.literal("[Gang Gems] Could not create a code; contact staff."));
            return 0;
        }
    }

    private static int staffBalance(ServerCommandSource source, Collection<GameProfile> players) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        if (players.size() != 1) {
            source.sendError(Text.literal("[Gang Gems] Specify exactly one known player."));
            return 0;
        }
        GameProfile player = players.iterator().next();
        source.sendFeedback(() -> Text.literal("[Gang Gems] " + player.getName() + ": "
            + store.balance(player.getId()) + " Gems; " + store.pendingCrafts(player.getId()).size()
            + " pending furniture deliveries."), false);
        return 1;
    }

    private static int status(ServerCommandSource source) {
        String bridge = discord != null ? "listener registered, Discord " + discord.status()
            : discordPending ? "waiting for Discord bot" : "inactive or unavailable (check logs)";
        source.sendFeedback(() -> Text.literal("[Gang Gems] Wallet: " + (wallet == null ? "unavailable" : "ready")
            + "; Discord: " + bridge + "; direct webhook: " + (webhook == null ? "off" : "on")
            + "; Gem furniture: " + (furnitureCraftingEnabled ? "on" : "off")
            + (wallet == null ? "" : "; pending orders: " + wallet.pendingPayments().size()) + "."), false);
        return 1;
    }

    private static int pending(ServerCommandSource source, int page) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        Map<String, Long> pending = store.pendingPayments();
        var entries = pending.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        long start = (long) (page - 1) * 10;
        if (start >= entries.size() && page > 1) {
            source.sendError(Text.literal("[Gang Gems] No pending orders on that page."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Gang Gems] Pending orders: " + entries.size() + " (page " + page + ")."), false);
        entries.stream().skip(start).limit(10).forEach(entry ->
            source.sendFeedback(() -> Text.literal(entry.getKey() + ": " + entry.getValue() + " Gems"), false));
        return 1;
    }

    private static int transaction(ServerCommandSource source, String transaction) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        WalletStore.PaymentView payment = store.payment(transaction);
        if (payment == null) {
            source.sendError(Text.literal("[Gang Gems] Transaction not found in the wallet."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Gang Gems] " + transaction + ": " + payment.gems() + " Gems; "
            + (payment.player() == null ? "pending assignment" : "credited to " + payment.player()) + "."), false);
        return 1;
    }

    private static int audit(ServerCommandSource source, int page) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        var entries = store.audit(null);
        long start = (long) (page - 1) * 10;
        if (start >= entries.size() && page > 1) {
            source.sendError(Text.literal("[Gang Gems] No audit entries on that page."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Gang Gems] Staff audit: " + entries.size() + " entries (page " + page + ")."), false);
        entries.stream().skip(start).limit(10).forEach(entry -> source.sendFeedback(() -> Text.literal(
            java.time.Instant.ofEpochMilli(entry.time()) + " " + entry.action() + " " + entry.gems()
            + " player=" + entry.player() + " staff=" + entry.actor()
            + (entry.reference().isEmpty() ? "" : " transaction=" + entry.reference())
            + " reason=" + entry.reason()), false));
        return 1;
    }

    private static int history(ServerCommandSource source, Collection<GameProfile> players, int page) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        if (players.size() != 1) {
            source.sendError(Text.literal("[Gang Gems] Specify exactly one known player."));
            return 0;
        }
        GameProfile player = players.iterator().next();
        var entries = store.paymentsFor(player.getId());
        long start = (long) (page - 1) * 10;
        if (start >= entries.size() && page > 1) {
            source.sendError(Text.literal("[Gang Gems] No recorded purchases on that page."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("[Gang Gems] " + player.getName() + ": " + entries.size()
            + " recorded purchases (page " + page + ", sorted by transaction ID)."), false);
        entries.stream().skip(start).limit(10).forEach(entry -> source.sendFeedback(() -> Text.literal(
            entry.transaction() + ": " + entry.gems() + " Gems"), false));
        return 1;
    }

    private static int adjust(ServerCommandSource source, Collection<GameProfile> players, long gems,
            boolean remove, String reason) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        if (players.size() != 1) {
            source.sendError(Text.literal("[Gang Gems] Specify exactly one known player."));
            return 0;
        }
        GameProfile player = players.iterator().next();
        try {
            WalletStore.Audit audit = store.adjust(player.getId(), gems, remove, actor(source), reason);
            LOGGER.info("Staff adjustment {} by {} for {}: {} {} Gems.",
                audit.id(), audit.actor(), player.getId(), remove ? "removed" : "granted", gems);
            source.sendFeedback(() -> Text.literal("[Gang Gems] " + (remove ? "Removed " : "Granted ")
                + gems + " Gems " + (remove ? "from " : "to ") + player.getName() + ". Audit: " + audit.id()), false);
            ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(player.getId());
            if (online != null) online.sendMessage(Text.literal("[Gang Gems] Staff "
                + (remove ? "removed " : "granted ") + gems + " Gems."), false);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            source.sendError(Text.literal("[Gang Gems] Invalid reason, insufficient Gems, or balance limit exceeded."));
            return 0;
        } catch (IOException failure) {
            LOGGER.error("Could not save a Gang Gems staff adjustment.", failure);
            source.sendError(Text.literal("[Gang Gems] Could not save the adjustment; contact staff."));
            return 0;
        }
    }

    private static String actor(ServerCommandSource source) {
        return source.getEntity() == null ? "console" : source.getPlayer().getUuid().toString();
    }

    private static int assign(ServerCommandSource source, String transaction, Collection<GameProfile> players) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        if (players.size() != 1) {
            source.sendError(Text.literal("[Gang Gems] Specify exactly one known player."));
            return 0;
        }

        GameProfile player = players.iterator().next();
        try {
            store.staffAssignPending(transaction, player.getId(), actor(source));
            source.sendFeedback(() -> Text.literal("[Gang Gems] Pending order assigned to " + player.getName() + "."), false);
            ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(player.getId());
            if (online != null) notifyPlayer(online);
            return 1;
        } catch (IllegalArgumentException | ArithmeticException failure) {
            source.sendError(Text.literal("[Gang Gems] Order is not pending or balance is too large."));
            return 0;
        } catch (IOException failure) {
            LOGGER.error("Could not assign a Gang Gems order.", failure);
            source.sendError(Text.literal("[Gang Gems] Could not save the order; contact staff."));
            return 0;
        }
    }

    private static int credit(ServerCommandSource source, String transaction, Collection<GameProfile> players, long gems) {
        WalletStore store = requireWallet(source);
        if (store == null) return 0;
        if (!transaction.matches("[A-Za-z0-9_-]{1,200}") || players.size() != 1) {
            source.sendError(Text.literal("[Gang Gems] Specify a valid transaction and exactly one known player."));
            return 0;
        }
        GameProfile player = players.iterator().next();
        try {
            WalletStore.CreditResult result = store.staffCreditPayment(transaction, player.getId(), gems, actor(source));
            if (result == WalletStore.CreditResult.DUPLICATE) {
                source.sendFeedback(() -> Text.literal("[Gang Gems] Transaction was already credited."), false);
                return 1;
            }
            source.sendFeedback(() -> Text.literal("[Gang Gems] Credited " + gems + " Gems to " + player.getName() + "."), false);
            ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(player.getId());
            if (online != null) notifyPlayer(online);
            return 1;
        } catch (IllegalStateException | ArithmeticException failure) {
            LOGGER.error("Gang Gems credit transaction conflicted or overflowed.", failure);
            source.sendError(Text.literal("[Gang Gems] Credit conflicted with an existing transaction or exceeded the balance limit."));
            return 0;
        } catch (IOException failure) {
            LOGGER.error("Could not save a Gang Gems credit.", failure);
            source.sendError(Text.literal("[Gang Gems] Could not save the credit; contact staff."));
            return 0;
        }
    }

    static void notifyPlayer(ServerPlayerEntity player) {
        WalletStore store = wallet;
        if (store == null) return;
        long gems = store.unannounced(player.getUuid());
        if (gems == 0) return;
        player.sendMessage(Text.literal("[Gang Gems] Thank you for your support! You received "
            + gems + " Gang Gems. Use /ganggems balance to check your balance."), false);
        try {
            store.acknowledgeNotification(player.getUuid(), gems);
        } catch (IOException | IllegalStateException failure) {
            LOGGER.error("Could not save a Gang Gems notification receipt.", failure);
            player.sendMessage(Text.literal("[Gang Gems] Notification receipt could not be saved; contact staff."), false);
        }
    }
}
