package com.thegangs.gangshats;

import java.util.Objects;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class GangsHats implements ModInitializer {
	public static final String MOD_ID = "gangshats";
	private static final String HAT_PERMISSION = "gangshats.command.hat";
	private static final String NICK_PERMISSION = "gangshats.command.nick";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(GangsHats::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		Objects.requireNonNull(registryAccess);
		Objects.requireNonNull(environment);
		dispatcher.register(CommandManager.literal("hat")
				.requires(source -> hasPermission(source, HAT_PERMISSION))
				.executes(context -> equipHat(context.getSource().getPlayer())));

		// Alias for Essential Commands' /nickname; re-dispatched at runtime so registration order between mods doesn't matter.
		dispatcher.register(CommandManager.literal("nick")
				.requires(source -> hasPermission(source, NICK_PERMISSION))
				.executes(context -> dispatcher.execute("nickname", context.getSource()))
				.then(CommandManager.argument("nicknameArgs", StringArgumentType.greedyString())
						.executes(context -> dispatcher.execute(
								"nickname " + StringArgumentType.getString(context, "nicknameArgs"),
								context.getSource()))));
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

	private static int equipHat(ServerPlayerEntity player) {
		ItemStack heldStack = player.getMainHandStack();
		if (heldStack.isEmpty()) {
			player.sendMessage(Text.literal("Hold an item first."), false);
			return 0;
		}

		ItemStack currentHeadStack = player.getEquippedStack(EquipmentSlot.HEAD);
		if (!currentHeadStack.isEmpty() && EnchantmentHelper.getLevel(Enchantments.BINDING_CURSE, currentHeadStack) > 0) {
			player.sendMessage(Text.literal("That hat is bound to you."), false);
			return 0;
		}

		ItemStack newHeadStack = heldStack.copy();
		ItemStack displacedHeadStack = currentHeadStack.copy();
		int selectedSlot = player.getInventory().selectedSlot;

		player.getInventory().setStack(selectedSlot, ItemStack.EMPTY);
		player.equipStack(EquipmentSlot.HEAD, newHeadStack);

		if (!displacedHeadStack.isEmpty()) {
			if (!player.getInventory().insertStack(displacedHeadStack)) {
				player.getInventory().setStack(selectedSlot, displacedHeadStack);
			}
		}

		player.getInventory().markDirty();
		player.sendMessage(Text.literal("Hat equipped."), true);
		return 1;
	}
}