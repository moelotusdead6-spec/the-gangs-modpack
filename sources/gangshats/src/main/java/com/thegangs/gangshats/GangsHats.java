package com.thegangs.gangshats;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;

public class GangsHats implements ModInitializer {
	public static final String MOD_ID = "gangshats";
	public static final String REDEEM_KEY_TAG = "gangshats_redeem_key";
	public static final String VOUCHER_TAG = "gangshats_voucher";
	public static final Identifier COSMETIC_SELECTION_PACKET = new Identifier(MOD_ID, "selection");
	private static final String VOUCHER_REWARD_TAG = "gangshats_reward_id";
	private static final Identifier TEMPEST_ID = new Identifier("simplyswords", "tempest");
	private static final String HAT_PERMISSION = "gangshats.command.hat";
	private static final String NICK_PERMISSION = "gangshats.command.nick";
	private static final Map<UUID, Integer> BOUNCEPAD_COOLDOWNS = new HashMap<>();
	private static final int PET_SYNC_INTERVAL = 40;
	private static int petSyncTicks;

	@Override
	public void onInitialize() {
		CosmeticItems.init();
		PetEntities.register();
		Bouncepads.register();
		CommandRegistrationCallback.EVENT.register(GangsHats::registerCommands);
		ServerTickEvents.END_SERVER_TICK.register(GangsHats::tickPets);
		ServerLifecycleEvents.SERVER_STARTED.register(GangsHats::removeLegacyPets);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			removeLegacyPets(server);
			sendSelections(handler.player);
			PetService.reconcile(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PetService.despawn(handler.player));
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
		for (String slot : new String[] {"hat", "halo", "back", "weapon", "pet"}) {
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
				.requires(source -> hasPermission(source, HAT_PERMISSION))
				.executes(context -> equipHat(context.getSource().getPlayer())));
		dispatcher.register(CommandManager.literal("cosmetics")
				.executes(context -> openCosmetics(context.getSource(), false)));
		dispatcher.register(CommandManager.literal("pets")
				.executes(context -> openCosmetics(context.getSource(), true)));
		dispatcher.register(CommandManager.literal("pet")
				.then(CommandManager.literal("recall")
						.executes(context -> recallPet(context.getSource()))));
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
		dispatcher.register(CommandManager.literal("bouncepad")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.literal("place")
						.then(CommandManager.argument("color", StringArgumentType.word())
							.suggests((context, builder) -> CommandSource.suggestMatching(
								new String[] {"red", "orange", "yellow", "green", "blue", "cyan", "purple", "white"}, builder))
							.executes(context -> placeBouncepad(context.getSource(), StringArgumentType.getString(context, "color")))))
				.then(CommandManager.literal("remove")
						.executes(context -> removeBouncepad(context.getSource()))));
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

	private static int openCosmetics(ServerCommandSource source, boolean petsOnly) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can open the cosmetics menu."));
			return 0;
		}
		CosmeticsGui.open(player, petsOnly);
		return 1;
	}

	private static int recallPet(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can recall a pet."));
			return 0;
		}
		CosmeticsGui.recall(player);
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

	private static int giveCosmeticKey(ServerPlayerEntity player) {
		ItemStack key = createRedeemKey();
		if (!player.giveItemStack(key)) {
			player.dropItem(key, false);
		}
		player.sendMessage(Text.literal("You received a Cosmetic & Pet Key."), false);
		return 1;
	}


	private static void tickPets(MinecraftServer server) {
		petSyncTicks++;
		boolean syncPets = petSyncTicks >= PET_SYNC_INTERVAL;
		if (syncPets) {
			petSyncTicks = 0;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (syncPets) {
				PetService.reconcile(player);
			}
			int cooldown = BOUNCEPAD_COOLDOWNS.getOrDefault(player.getUuid(), 0);
			if (cooldown > 0) {
				BOUNCEPAD_COOLDOWNS.put(player.getUuid(), cooldown - 1);
			}
			if (cooldown == 0 && player.isOnGround() && player.getVelocity().y > 0.0D
					&& Bouncepads.is(player.getWorld().getBlockState(player.getBlockPos().down()).getBlock())) {
				Vec3d horizontal = new Vec3d(player.getVelocity().x, 0.0D, player.getVelocity().z);
				if (horizontal.lengthSquared() < 0.0001D) {
					horizontal = player.getRotationVector().multiply(1.0D, 0.0D, 1.0D);
				}
				horizontal = horizontal.normalize().multiply(1.15D);
				player.setVelocity(horizontal.x, 1.0D, horizontal.z);
				player.velocityDirty = true;
				BOUNCEPAD_COOLDOWNS.put(player.getUuid(), 8);
			}
		}
	}

	private static int removeBouncepad(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can remove bouncepads."));
			return 0;
		}
		if (!(player.raycast(8.0D, 1.0F, false) instanceof BlockHitResult hit)) {
			source.sendError(Text.literal("Look at a bouncepad first."));
			return 0;
		}
		BlockPos target = hit.getBlockPos();
		if (!Bouncepads.is(player.getWorld().getBlockState(target).getBlock())) {
			source.sendError(Text.literal("That block is not a bouncepad."));
			return 0;
		}
		player.getWorld().breakBlock(target, false, player);
		source.sendFeedback(() -> Text.literal("Removed bouncepad."), true);
		return 1;
	}

	private static int placeBouncepad(ServerCommandSource source, String color) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only players can place bouncepads."));
			return 0;
		}
		String[] colors = {"red", "orange", "yellow", "green", "blue", "cyan", "purple", "white"};
		boolean valid = false;
		for (String allowed : colors) {
			if (allowed.equals(color)) {
				valid = true;
				break;
			}
		}
		if (!valid) {
			source.sendError(Text.literal("Unknown bouncepad color."));
			return 0;
		}
		player.getWorld().setBlockState(player.getBlockPos().down(), Bouncepads.get(color).getDefaultState());
		source.sendFeedback(() -> Text.literal("Placed an admin bouncepad: " + color), true);
		return 1;
	}
}