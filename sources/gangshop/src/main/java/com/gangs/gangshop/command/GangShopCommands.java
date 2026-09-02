/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.arguments.LongArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.ArgumentBuilder
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.builder.RequiredArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
 *  net.minecraft.command.argument.EntityArgumentType
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.command.CommandManager
 *  net.minecraft.server.command.ServerCommandSource
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.text.Text
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.UserCache
 */
package com.gangs.gangshop.command;

import com.gangs.gangshop.GangShopMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;

public final class GangShopCommands {
    private GangShopCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GangShopCommands.register((CommandDispatcher<ServerCommandSource>)dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder)CommandManager.literal((String)"shop").executes(ctx -> GangShopCommands.openShop((ServerCommandSource)ctx.getSource())));
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"bal").executes(ctx -> GangShopCommands.balanceSelf((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).executes(ctx -> GangShopCommands.balanceTarget((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player")))));
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"balance").executes(ctx -> GangShopCommands.balanceSelf((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).executes(ctx -> GangShopCommands.balanceTarget((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player")))));
        dispatcher.register((LiteralArgumentBuilder)CommandManager.literal((String)"pay").then(CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).then(CommandManager.argument((String)"amount", (ArgumentType)LongArgumentType.longArg((long)1L)).executes(ctx -> GangShopCommands.pay((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player"), LongArgumentType.getLong((CommandContext)ctx, (String)"amount"))))));
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"baltop").executes(ctx -> GangShopCommands.baltop((ServerCommandSource)ctx.getSource(), 10))).then(CommandManager.argument((String)"limit", (ArgumentType)IntegerArgumentType.integer((int)1, (int)50)).executes(ctx -> GangShopCommands.baltop((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"limit")))));
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"shopedit").requires(source -> source.hasPermissionLevel(2))).executes(ctx -> GangShopCommands.openAdminEditor((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"reload").executes(ctx -> GangShopCommands.reload((ServerCommandSource)ctx.getSource())))).then(GangShopCommands.priceShowCommand())).then(GangShopCommands.priceSetCommand()));
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"gangshopedit").requires(source -> source.hasPermissionLevel(2))).executes(ctx -> GangShopCommands.openAdminEditor((ServerCommandSource)ctx.getSource())));
        LiteralArgumentBuilder gangShopMoneyCommand = (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"money").then(GangShopCommands.moneyAddCommand())).then(GangShopCommands.moneyRemoveCommand())).then(GangShopCommands.moneySetCommand())).then(GangShopCommands.moneyViewCommand());
        LiteralArgumentBuilder gangShopPriceCommand = (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"price").then(CommandManager.literal((String)"regenerate").executes(ctx -> GangShopCommands.regeneratePrices((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"edit").then(GangShopCommands.priceSetCommand()))).then(GangShopCommands.priceShowCommand());
        dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"gangshop").requires(source -> source.hasPermissionLevel(2))).executes(ctx -> GangShopCommands.helpShopEdit((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"reload").executes(ctx -> GangShopCommands.reload((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"edit").executes(ctx -> GangShopCommands.openAdminEditor((ServerCommandSource)ctx.getSource())))).then((ArgumentBuilder)gangShopMoneyCommand)).then((ArgumentBuilder)gangShopPriceCommand));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> priceSetCommand() {
        RequiredArgumentBuilder buyArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"buy", (ArgumentType)LongArgumentType.longArg((long)1L)).executes(ctx -> GangShopCommands.editShopPrice((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"item"), LongArgumentType.getLong((CommandContext)ctx, (String)"sell"), LongArgumentType.getLong((CommandContext)ctx, (String)"buy")));
        RequiredArgumentBuilder sellArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"sell", (ArgumentType)LongArgumentType.longArg((long)1L)).then((ArgumentBuilder)buyArgument);
        RequiredArgumentBuilder itemArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"item", (ArgumentType)StringArgumentType.string()).then((ArgumentBuilder)sellArgument);
        return (LiteralArgumentBuilder)CommandManager.literal((String)"set").then((ArgumentBuilder)itemArgument);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> priceShowCommand() {
        RequiredArgumentBuilder itemArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"item", (ArgumentType)StringArgumentType.string()).executes(ctx -> GangShopCommands.showShopPrice((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"item")));
        return (LiteralArgumentBuilder)CommandManager.literal((String)"show").then((ArgumentBuilder)itemArgument);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moneyAddCommand() {
        RequiredArgumentBuilder amountArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"amount", (ArgumentType)LongArgumentType.longArg((long)1L)).executes(ctx -> GangShopCommands.adminAddMoney((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player"), LongArgumentType.getLong((CommandContext)ctx, (String)"amount")));
        RequiredArgumentBuilder playerArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).then((ArgumentBuilder)amountArgument);
        return (LiteralArgumentBuilder)CommandManager.literal((String)"add").then((ArgumentBuilder)playerArgument);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moneyRemoveCommand() {
        RequiredArgumentBuilder amountArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"amount", (ArgumentType)LongArgumentType.longArg((long)1L)).executes(ctx -> GangShopCommands.adminRemoveMoney((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player"), LongArgumentType.getLong((CommandContext)ctx, (String)"amount")));
        RequiredArgumentBuilder playerArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).then((ArgumentBuilder)amountArgument);
        return (LiteralArgumentBuilder)CommandManager.literal((String)"remove").then((ArgumentBuilder)playerArgument);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moneySetCommand() {
        RequiredArgumentBuilder amountArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"amount", (ArgumentType)LongArgumentType.longArg((long)0L)).executes(ctx -> GangShopCommands.adminSetMoney((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player"), LongArgumentType.getLong((CommandContext)ctx, (String)"amount")));
        RequiredArgumentBuilder playerArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).then((ArgumentBuilder)amountArgument);
        return (LiteralArgumentBuilder)CommandManager.literal((String)"set").then((ArgumentBuilder)playerArgument);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moneyViewCommand() {
        RequiredArgumentBuilder playerArgument = (RequiredArgumentBuilder)CommandManager.argument((String)"player", (ArgumentType)EntityArgumentType.player()).executes(ctx -> GangShopCommands.adminViewMoney((ServerCommandSource)ctx.getSource(), EntityArgumentType.getPlayer((CommandContext)ctx, (String)"player")));
        return (LiteralArgumentBuilder)CommandManager.literal((String)"view").then((ArgumentBuilder)playerArgument);
    }

    private static int openAdminEditor(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError((Text)Text.literal((String)"Only players can use the admin shop editor."));
            return 0;
        }
        GangShopMod.GUI.openAdminEditor(player);
        source.sendFeedback(() -> Text.literal((String)"Opening Gang Shop admin editor."), false);
        return 1;
    }

    private static int openShop(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError((Text)Text.literal((String)"Only players can use /shop."));
            return 0;
        }
        GangShopMod.GUI.openMainMenu(player);
        return 1;
    }

    private static int balanceSelf(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError((Text)Text.literal((String)"Use /bal <player> when running from console."));
            return 0;
        }
        long balance = GangShopMod.ECONOMY.getBalance(player);
        source.sendFeedback(() -> Text.literal((String)("Balance: " + String.format("%,d", balance) + " Gang Bucks")), false);
        return 1;
    }

    private static int balanceTarget(ServerCommandSource source, ServerPlayerEntity target) {
        long balance = GangShopMod.ECONOMY.getBalance(target);
        source.sendFeedback(() -> Text.literal((String)(target.getName().getString() + " balance: " + String.format("%,d", balance) + " Gang Bucks")), false);
        return 1;
    }

    private static int pay(ServerCommandSource source, ServerPlayerEntity target, long amount) {
        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) {
            source.sendError((Text)Text.literal((String)"Only players can use /pay."));
            return 0;
        }
        if (sender.getUuid().equals(target.getUuid())) {
            source.sendError((Text)Text.literal((String)"You cannot pay yourself."));
            return 0;
        }
        boolean removed = GangShopMod.ECONOMY.subtract(sender.getUuid(), amount);
        if (!removed) {
            source.sendError((Text)Text.literal((String)("Insufficient balance to pay " + String.format("%,d", amount) + " Gang Bucks.")));
            return 0;
        }
        GangShopMod.ECONOMY.add(target.getUuid(), amount);
        long senderBalance = GangShopMod.ECONOMY.getBalance(sender);
        long targetBalance = GangShopMod.ECONOMY.getBalance(target);
        source.sendFeedback(() -> Text.literal((String)("Paid " + target.getName().getString() + " " + String.format("%,d", amount) + " Gang Bucks. Your new balance: " + String.format("%,d", senderBalance))), false);
        target.sendMessage((Text)Text.literal((String)(sender.getName().getString() + " paid you " + String.format("%,d", amount) + " Gang Bucks. New balance: " + String.format("%,d", targetBalance))), false);
        return 1;
    }

    private static int baltop(ServerCommandSource source, int limit) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendError((Text)Text.literal((String)"Server context unavailable."));
            return 0;
        }
        List<Map.Entry<UUID, Long>> top = GangShopMod.ECONOMY.topBalances(limit);
        if (top.isEmpty()) {
            source.sendFeedback(() -> Text.literal((String)"No wallets tracked yet."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal((String)("--- Gang Bucks Top " + limit + " ---")), false);
        UserCache userCache = server.getUserCache();
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top) {
            String name = userCache != null ? userCache.getByUuid(entry.getKey()).map(profile -> profile.getName()).orElse(entry.getKey().toString()) : entry.getKey().toString();
            int currentRank = rank++;
            source.sendFeedback(() -> Text.literal((String)(currentRank + ". " + name + " - " + String.format("%,d", entry.getValue()))), false);
        }
        return top.size();
    }

    private static int reload(ServerCommandSource source) {
        GangShopMod.reloadAll();
        source.sendFeedback(() -> Text.literal((String)"Gang Shop reloaded (catalog + prices)."), true);
        return 1;
    }

    private static int regeneratePrices(ServerCommandSource source) {
        GangShopMod.reloadAll();
        source.sendFeedback(() -> Text.literal((String)"Gang Shop prices regenerated for missing entries."), true);
        return 1;
    }

    private static int helpShopEdit(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal((String)"Usage: /shopedit set <item_id> <sell> <buy> | /shopedit show <item_id> | /shopedit reload"), false);
        return 1;
    }

    private static int showShopPrice(ServerCommandSource source, String itemName) {
        Identifier id = Identifier.tryParse((String)itemName);
        if (id == null) {
            source.sendError((Text)Text.literal((String)("Invalid item id: " + itemName + ". Use format like minecraft:emerald_block.")));
            return 0;
        }
        long sell = GangShopMod.PRICES.getSellPrice(id);
        long buy = GangShopMod.PRICES.getBuyPrice(id);
        source.sendFeedback(() -> Text.literal((String)(String.valueOf(id) + " -> sell: " + String.format("%,d", sell) + ", buy: " + String.format("%,d", buy) + " Gang Bucks")), false);
        return 1;
    }

    private static int editShopPrice(ServerCommandSource source, String itemName, long sell, long buy) {
        Identifier id = Identifier.tryParse((String)itemName);
        if (id == null) {
            source.sendError((Text)Text.literal((String)("Invalid item id: " + itemName + ". Use format like minecraft:emerald_block.")));
            return 0;
        }
        GangShopMod.PRICES.setItemPrice(id, sell, buy);
        GangShopMod.reloadAll();
        source.sendFeedback(() -> Text.literal((String)("Updated " + String.valueOf(id) + " sell=" + String.format("%,d", sell) + ", buy=" + String.format("%,d", buy) + " Gang Bucks.")), true);
        return 1;
    }

    private static int adminAddMoney(ServerCommandSource source, ServerPlayerEntity target, long amount) {
        GangShopMod.ECONOMY.add(target.getUuid(), amount);
        long balance = GangShopMod.ECONOMY.getBalance(target);
        source.sendFeedback(() -> Text.literal((String)("Added " + String.format("%,d", amount) + " Gang Bucks to " + target.getName().getString() + ". New balance: " + String.format("%,d", balance))), true);
        target.sendMessage((Text)Text.literal((String)("Admin added " + String.format("%,d", amount) + " Gang Bucks. New balance: " + String.format("%,d", balance))), false);
        return 1;
    }

    private static int adminRemoveMoney(ServerCommandSource source, ServerPlayerEntity target, long amount) {
        boolean removed = GangShopMod.ECONOMY.subtract(target.getUuid(), amount);
        if (!removed) {
            source.sendError((Text)Text.literal((String)("Cannot remove " + String.format("%,d", amount) + ": player has insufficient balance.")));
            return 0;
        }
        long balance = GangShopMod.ECONOMY.getBalance(target);
        source.sendFeedback(() -> Text.literal((String)("Removed " + String.format("%,d", amount) + " Gang Bucks from " + target.getName().getString() + ". New balance: " + String.format("%,d", balance))), true);
        target.sendMessage((Text)Text.literal((String)("Admin removed " + String.format("%,d", amount) + " Gang Bucks. New balance: " + String.format("%,d", balance))), false);
        return 1;
    }

    private static int adminSetMoney(ServerCommandSource source, ServerPlayerEntity target, long amount) {
        GangShopMod.ECONOMY.set(target.getUuid(), amount);
        source.sendFeedback(() -> Text.literal((String)("Set " + target.getName().getString() + " balance to " + String.format("%,d", amount) + " Gang Bucks.")), true);
        target.sendMessage((Text)Text.literal((String)("Admin set your balance to " + String.format("%,d", amount) + " Gang Bucks.")), false);
        return 1;
    }

    private static int adminViewMoney(ServerCommandSource source, ServerPlayerEntity target) {
        long balance = GangShopMod.ECONOMY.getBalance(target);
        source.sendFeedback(() -> Text.literal((String)(target.getName().getString() + " balance: " + String.format("%,d", balance) + " Gang Bucks")), false);
        return 1;
    }
}

