package com.gangs.gangsales;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;

import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;

public final class GangSalesCommands {
	private static final String EC_PERMISSION = "gangsales.command.ec";
	private static final String GS_PERMISSION = "gangsales.command.gs";
	private static final String GS_HISTORY_PERMISSION = "gangsales.command.gs.history";
	private static final String GS_MINE_PERMISSION = "gangsales.command.gs.mine";
	private static final String GS_ADD_PERMISSION = "gangsales.command.gs.add";

	private GangSalesCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		dispatcher.register(CommandManager.literal("enderchest")
				.requires(source -> hasPermission(source, EC_PERMISSION))
				.executes(context -> openEnderChest(context.getSource())));
		dispatcher.register(CommandManager.literal("ec")
				.requires(source -> hasPermission(source, EC_PERMISSION))
				.executes(context -> openEnderChest(context.getSource())));
		dispatcher.register(CommandManager.literal("gs")
				.requires(source -> hasPermission(source, GS_PERMISSION))
				.executes(context -> open(context.getSource()))
				.then(CommandManager.literal("history")
						.requires(source -> hasPermission(source, GS_HISTORY_PERMISSION))
						.executes(context -> openHistory(context.getSource())))
				.then(CommandManager.literal("mine")
						.requires(source -> hasPermission(source, GS_MINE_PERMISSION))
						.executes(context -> openMine(context.getSource())))
				.then(CommandManager.literal("add")
						.requires(source -> hasPermission(source, GS_ADD_PERMISSION))
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
								.then(CommandManager.argument("price", LongArgumentType.longArg(1))
										.executes(context -> add(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), LongArgumentType.getLong(context, "price")))))));
	}

	private static boolean hasPermission(ServerCommandSource source, String permission) {
		if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
			return true;
		}

		if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
			return true;
		}
		try {
			var user = LuckPermsProvider.get().getUserManager().getUser(player.getUuid());
			if (user == null) {
				GangSalesMod.LOGGER.warn("Cannot check {}: LuckPerms user {} is not loaded", permission, player.getUuid());
				return false;
			}
			return user.getCachedData().getPermissionData(user.getQueryOptions()).checkPermission(permission) != Tristate.FALSE;
		} catch (IllegalStateException failure) {
			GangSalesMod.LOGGER.error("LuckPerms permission check failed for {} ({})", player.getUuid(), permission, failure);
			return false;
		}
	}

	private static int open(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		GangSalesGui.open(source.getPlayer(), GangSalesGui.View.BROWSE, 0);
		return 1;
	}

	private static int openEnderChest(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayer();
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, ignored) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, player.getEnderChestInventory()), Text.translatable("container.enderchest")));
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