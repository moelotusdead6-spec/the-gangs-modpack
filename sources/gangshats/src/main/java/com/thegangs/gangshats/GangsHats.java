package com.thegangs.gangshats;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class GangsHats implements ModInitializer {
	public static final String MOD_ID = "gangshats";
	private static final String HAT_PERMISSION = "gangshats.command.hat";
	private static final String NICK_PERMISSION = "gangshats.command.nick";
	private static final int[] COSMETIC_MODEL_DATA = {
			9000, 9001, 9002, 9003, 9004, 9005, 9006, 9007, 9008, 9009, 9010, 9011,
			10000, 10001, 10002, 10003, 10004, 10005,
			6181, 6182, 6183, 6184, 6185, 6186, 6187
	};
	private static final int[] SWORD_MODEL_DATA = {20000, 20001, 20002};
	private static final int[] PET_MODEL_DATA = {30000, 30001, 30002, 30003, 30004, 30005, 30006, 30007, 30008, 30009, 30010};

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(GangsHats::registerCommands);
		ServerTickEvents.END_SERVER_TICK.register(GangsHats::tickPets);
	}

	private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
		Objects.requireNonNull(registryAccess);
		Objects.requireNonNull(environment);
		dispatcher.register(CommandManager.literal("hat")
				.requires(source -> hasPermission(source, HAT_PERMISSION))
				.executes(context -> equipHat(context.getSource().getPlayer())));
		dispatcher.register(CommandManager.literal("cosmeticgive")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveRandomCosmetic(EntityArgumentType.getPlayer(context, "player")))));
		dispatcher.register(CommandManager.literal("cosmeticgiveinternal")
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveRandomCosmetic(EntityArgumentType.getPlayer(context, "player")))));
		dispatcher.register(CommandManager.literal("bouncepad")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("color", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(
							new String[] {"red", "orange", "yellow", "green", "blue", "cyan", "purple", "white"}, builder))
						.executes(context -> placeBouncepad(context.getSource(), StringArgumentType.getString(context, "color")))));
		dispatcher.register(CommandManager.literal("petgive")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveRandomPet(EntityArgumentType.getPlayer(context, "player")))));
		dispatcher.register(CommandManager.literal("petgiveinternal")
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.executes(context -> giveRandomPet(EntityArgumentType.getPlayer(context, "player")))));

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

	private static int giveRandomCosmetic(ServerPlayerEntity player) {
		if (ThreadLocalRandom.current().nextInt(4) == 0) {
			return giveRandomPet(player);
		}
		if (ThreadLocalRandom.current().nextInt(5) == 0) {
			return giveRandomSword(player);
		}
		int modelData = COSMETIC_MODEL_DATA[ThreadLocalRandom.current().nextInt(COSMETIC_MODEL_DATA.length)];
		ItemStack cosmetic = new ItemStack(Items.PAPER);
		cosmetic.getOrCreateNbt().putInt("CustomModelData", modelData);
		if (modelData < 10000) {
			cosmetic.getOrCreateNbt().putBoolean("gangshats_wing", true);
		}
		if (!player.giveItemStack(cosmetic)) {
			player.dropItem(cosmetic, false);
		}
		player.sendMessage(Text.literal("You received a random cosmetic."), false);
		return 1;
	}

	private static int giveRandomSword(ServerPlayerEntity player) {
		int modelData = SWORD_MODEL_DATA[ThreadLocalRandom.current().nextInt(SWORD_MODEL_DATA.length)];
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		sword.getOrCreateNbt().putInt("CustomModelData", modelData);
		if (!player.giveItemStack(sword)) {
			player.dropItem(sword, false);
		}
		player.sendMessage(Text.literal("You received a random cosmetic sword."), false);
		return 1;
	}

	private static int giveRandomPet(ServerPlayerEntity player) {
		int modelData = PET_MODEL_DATA[ThreadLocalRandom.current().nextInt(PET_MODEL_DATA.length)];
		ArmorStandEntity pet = new ArmorStandEntity(player.getWorld(), player.getX() + 1.0D, player.getY(), player.getZ());
		pet.setInvisible(true);
		pet.setNoGravity(true);
		pet.addCommandTag("gangs_pet");
		pet.addCommandTag("gangs_pet_owner_" + player.getUuid());
		ItemStack model = new ItemStack(Items.PAPER);
		model.getOrCreateNbt().putInt("CustomModelData", modelData);
		pet.equipStack(EquipmentSlot.HEAD, model);
		player.getWorld().spawnEntity(pet);
		player.sendMessage(Text.literal("You received a random pet."), false);
		return 1;
	}

	private static void tickPets(net.minecraft.server.MinecraftServer server) {
		for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
			for (ArmorStandEntity pet : world.getEntitiesByType(EntityType.ARMOR_STAND,
					entity -> entity.getCommandTags().contains("gangs_pet"))) {
				String ownerTag = pet.getCommandTags().stream()
						.filter(tag -> tag.startsWith("gangs_pet_owner_"))
						.findFirst().orElse(null);
				if (ownerTag == null) {
					continue;
				}
				try {
					ServerPlayerEntity owner = server.getPlayerManager().getPlayer(UUID.fromString(ownerTag.substring("gangs_pet_owner_".length())));
					if (owner == null || owner.getWorld() != world) {
						continue;
					}
					pet.refreshPositionAndAngles(owner.getX() + 1.0D, owner.getY() + 0.25D, owner.getZ() + 1.0D, owner.getYaw(), 0.0F);
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
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
		int modelData = switch (color) {
			case "red" -> 40000;
			case "orange" -> 40001;
			case "yellow" -> 40002;
			case "green" -> 40003;
			case "blue" -> 40004;
			case "cyan" -> 40005;
			case "purple" -> 40006;
			default -> 40007;
		};
		int result = source.getServer().getCommandManager().executeWithPrefix(
				source, "setblock ~ ~-1 ~ minecraft:" + color + "_concrete");
		source.getServer().getCommandManager().executeWithPrefix(source,
				"summon minecraft:armor_stand ~ ~ ~ {Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"gangs_bouncepad\"],ArmorItems:[{},{},{},{id:\"minecraft:paper\",Count:1b,tag:{CustomModelData:" + modelData + "}}]}");
		source.sendFeedback(() -> Text.literal("Placed an admin bouncepad: " + color), true);
		return result;
	}
}