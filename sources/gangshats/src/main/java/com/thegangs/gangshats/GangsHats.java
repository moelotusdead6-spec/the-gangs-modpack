package com.thegangs.gangshats;

import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.network.PacketByteBuf;

public class GangsHats implements ModInitializer {
	public static final String MOD_ID = "gangshats";
	public static final String REDEEM_KEY_TAG = "gangshats_redeem_key";
	public static final String VOUCHER_TAG = "gangshats_voucher";
	public static final Identifier COSMETIC_SELECTION_PACKET = new Identifier(MOD_ID, "selection");
	private static final String VOUCHER_REWARD_TAG = "gangshats_reward_id";
	private static final Identifier TEMPEST_ID = new Identifier("simplyswords", "tempest");
	private static final String NICK_PERMISSION = "gangshats.command.nick";
	@Override
	public void onInitialize() {
		CosmeticItems.init();
		CommandRegistrationCallback.EVENT.register(GangsHats::registerCommands);
		ServerLifecycleEvents.SERVER_STARTED.register(GangsHats::removeLegacyPets);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			removeLegacyPets(server);
			sendSelections(handler.player);
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return TypedActionResult.pass(stack);
			}
			if (isVoucher(stack)) {
				return redeemVoucher(serverPlayer, stack);
			}
			if (!isRedeemKey(stack)) {
				return TypedActionResult.pass(stack);
			}
			return redeemKey(serverPlayer, stack);
		});
	}

	private static TypedActionResult<ItemStack> redeemKey(ServerPlayerEntity player, ItemStack key) {
		List<Item> pool = CosmeticItems.all();
		ItemStack reward = new ItemStack(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));
		String rewardId = Registries.ITEM.getId(reward.getItem()).toString();
		boolean firstUnlock = CosmeticUnlockState.get(player.getServer()).unlock(player.getUuid(), rewardId);
		if (!player.getAbilities().creativeMode) {
			key.decrement(1);
		}
		ItemStack payout = firstUnlock ? reward : createVoucher(reward);
		if (!player.giveItemStack(payout)) {
			player.dropItem(payout, false);
		}
		player.sendMessage(Text.literal(firstUnlock ? "Unlocked cosmetic: " : "Duplicate reward voucher: ")
				.append(reward.getName()), false);
		return TypedActionResult.success(key);
	}

	// Clears invisible armor-stand pets left behind by the pre-entity cosmetic system.
	private static void removeLegacyPets(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (ArmorStandEntity stand : world.getEntitiesByType(EntityType.ARMOR_STAND,
					entity -> entity.getCommandTags().contains("gangs_pet"))) {
				stand.discard();
			}
		}
	}

	private static void sendSelections(ServerPlayerEntity player) {
		CosmeticUnlockState state = CosmeticUnlockState.get(player.getServer());
		for (String slot : new String[] {"halo", "weapon"}) {
			String rewardId = state.selected(player.getUuid(), slot);
			if (rewardId == null) {
				continue;
			}
			sendSelection(player, slot, rewardId);
		}
	}

	public static void sendSelection(ServerPlayerEntity player, String slot, String rewardId) {
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeString(slot);
		buffer.writeString(rewardId);
		buffer.writeUuid(player.getUuid());
		ServerPlayNetworking.send(player, COSMETIC_SELECTION_PACKET, buffer);
	}

	private static boolean isRedeemKey(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.isOf(CosmeticItems.COSMETIC_KEY)) {
			return true;
		}
		// Keys issued before 1.0.2 were NBT-marked Simply Swords Tempest stacks.
		return stack.getItem() == Registries.ITEM.get(TEMPEST_ID)
				&& stack.getNbt() != null && stack.getNbt().getBoolean(REDEEM_KEY_TAG);
	}

	private static ItemStack createRedeemKey() {
		return new ItemStack(CosmeticItems.COSMETIC_KEY);
	}

	private static boolean isVoucher(ItemStack stack) {
		return !stack.isEmpty() && stack.getNbt() != null && stack.getNbt().getBoolean(VOUCHER_TAG)
				&& stack.getNbt().contains(VOUCHER_REWARD_TAG);
	}

	private static ItemStack createVoucher(ItemStack reward) {
		String path = Registries.ITEM.getId(reward.getItem()).getPath();
		ItemStack voucher = new ItemStack(CosmeticItems.COSMETIC_VOUCHER);
		voucher.getOrCreateNbt().putBoolean(VOUCHER_TAG, true);
		voucher.getOrCreateNbt().putString(VOUCHER_REWARD_TAG, Registries.ITEM.getId(reward.getItem()).toString());
		voucher.setCustomName(Text.literal("Cosmetic Voucher: ").append(reward.getName()));
		return voucher;
	}

	private static TypedActionResult<ItemStack> redeemVoucher(ServerPlayerEntity player, ItemStack voucher) {
		String rewardId = voucher.getNbt().getString(VOUCHER_REWARD_TAG);
		Item rewardItem = Registries.ITEM.get(new Identifier(rewardId));
		if (!CosmeticItems.all().contains(rewardItem)) {
			player.sendMessage(Text.literal("This voucher is invalid."), false);
			return TypedActionResult.fail(voucher);
		}
		CosmeticUnlockState.get(player.getServer()).unlock(player.getUuid(), rewardId);
		if (!player.getAbilities().creativeMode) {
			voucher.decrement(1);
		}
		player.sendMessage(Text.literal("Unlocked cosmetic: ").append(rewardItem.getName()), false);
		return TypedActionResult.success(voucher);
	}

	private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		Objects.requireNonNull(registryAccess);
		Objects.requireNonNull(environment);
		dispatcher.register(CommandManager.literal("hat")
				.executes(context -> equipHat(context.getSource().getPlayer())));
		dispatcher.register(CommandManager.literal("hats")
				.executes(context -> equipHat(context.getSource().getPlayer())));
		dispatcher.register(CommandManager.literal("cosmetics")
				.executes(context -> openCosmetics(context.getSource())));
		dispatcher.register(CommandManager.literal("clearcosmetics")
				.executes(context -> clearCosmetics(context.getSource())));
		dispatcher.register(CommandManager.literal("cosmetickey")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveCosmeticKey(EntityArgumentType.getPlayer(context, "player")))));
		dispatcher.register(CommandManager.literal("cosmetic")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("key")
						.then(CommandManager.argument("player", EntityArgumentType.player())
							.executes(context -> giveCosmeticKey(EntityArgumentType.getPlayer(context, "player"))))));
		dispatcher.register(CommandManager.literal("cosmetickeyinternal")
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveCosmeticKey(EntityArgumentType.getPlayer(context, "player")))));
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

	private static int openCosmetics(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can open the cosmetics menu."));
			return 0;
		}
		CosmeticsGui.open(player);
		return 1;
	}

	private static Object getPermissionData(Object luckPerms, Object cachedData, ServerPlayerEntity player) throws ReflectiveOperationException {
		try {
			Class<?> queryOptionsClass = Class.forName("net.luckperms.api.query.QueryOptions");
			Object contextManager = luckPerms.getClass().getMethod("getContextManager").invoke(luckPerms);
			Object queryOptions = contextManager.getClass().getMethod("getQueryOptions", Object.class).invoke(contextManager, player);
			Object options = queryOptions instanceof Optional<?> optional ? optional.orElse(null) : queryOptions;
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

	public static void clearCosmetics(ServerPlayerEntity player) {
		CosmeticUnlockState state = CosmeticUnlockState.get(player.getServer());
		for (String slot : new String[] {"hat", "halo", "back", "weapon", "pet"}) {
			state.clear(player.getUuid(), slot);
			sendSelection(player, slot, "");
		}
	}

	private static int clearCosmetics(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can clear their cosmetics."));
			return 0;
		}
		clearCosmetics(player);
		player.sendMessage(Text.literal("Cosmetics cleared."), false);
		return 1;
	}

	private static int giveCosmeticKey(ServerPlayerEntity player) {
		ItemStack key = createRedeemKey();
		if (!player.giveItemStack(key)) {
			player.dropItem(key, false);
		}
		player.sendMessage(Text.literal("You received a Cosmetic Key."), false);
		return 1;
	}


}