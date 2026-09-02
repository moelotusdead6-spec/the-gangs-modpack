package com.gangs.gangsales;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.Optional;

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

		try {
			Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
			Object luckPerms = providerClass.getMethod("get").invoke(null);
			Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
			Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUuid());
			if (user == null) {
				return true;
			}
			Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
			Object permissionData = getPermissionData(luckPerms, cachedData, player);
			Object result = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, permission);
			return (Boolean) result.getClass().getMethod("asBoolean").invoke(result);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			return true;
		}
	}

	private static Object getPermissionData(Object luckPerms, Object cachedData, ServerPlayerEntity player) throws ReflectiveOperationException {
		try {
			Class<?> queryOptionsClass = Class.forName("net.luckperms.api.query.QueryOptions");
			Object contextManager = luckPerms.getClass().getMethod("getContextManager").invoke(luckPerms);
			Optional<?> queryOptions = (Optional<?>) contextManager.getClass().getMethod("getQueryOptions", Object.class).invoke(contextManager, player);
			Object options = queryOptions.orElse(null);
			if (options == null) {
				options = queryOptionsClass.getMethod("defaultContextualOptions").invoke(null);
			}
			return cachedData.getClass().getMethod("getPermissionData", queryOptionsClass).invoke(cachedData, options);
		} catch (NoSuchMethodException ignored) {
		}

		return cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
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