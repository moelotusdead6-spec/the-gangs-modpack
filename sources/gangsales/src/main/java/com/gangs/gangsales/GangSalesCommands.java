package com.gangs.gangsales;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class GangSalesCommands {
	private GangSalesCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("gs")
				.executes(context -> open(context.getSource()))
				.then(CommandManager.literal("history").executes(context -> openHistory(context.getSource())))
				.then(CommandManager.literal("mine").executes(context -> openMine(context.getSource())))
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
								.then(CommandManager.argument("price", LongArgumentType.longArg(1))
										.executes(context -> add(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), LongArgumentType.getLong(context, "price")))))));
	}

	private static int open(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GangSalesGui.open(source.getPlayer(), GangSalesGui.View.BROWSE, 0);
		return 1;
	}

	private static int openHistory(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GangSalesGui.open(source.getPlayer(), GangSalesGui.View.HISTORY, 0);
		return 1;
	}

	private static int openMine(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GangSalesGui.open(source.getPlayer(), GangSalesGui.View.HISTORY, 0);
		return 1;
	}

	private static int add(ServerCommandSource source, int amount, long price) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayer();
		SaleListing listing = GangSalesMod.SALES.create(player, amount, price);
		if (listing == null) {
			player.sendMessage(Text.literal("Hold at least that many items, use a price of 1 or more, and keep fewer than 30 active listings."), false);
			return 0;
		}
		player.sendMessage(Text.literal("Listed " + amount + " item(s) for " + price + " Gang Bucks."), false);
		return 1;
	}
}