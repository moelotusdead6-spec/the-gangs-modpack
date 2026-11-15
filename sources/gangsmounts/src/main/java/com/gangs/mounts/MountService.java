package com.gangs.mounts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.gangs.mounts.mixin.MountDataAccess;

import com.thegangs.gangshats.CosmeticAccess;
import com.thegangs.gangshats.CosmeticUnlockState;
import com.thegangs.gangshats.GangsHats;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.PersistentState;

public final class MountService implements ModInitializer {
	public static final String SLOT = "mount";
	public static final Identifier CATALOG = new Identifier("gangsmounts", "catalog");
	public static final Identifier QUICK_ACTION = new Identifier("gangsmounts", "quick_action");
	private static final Identifier SUMMONING_STAFF = new Identifier("mythicmounts", "summoning_staff");
	private static final String TAG = "gangsmounts_cosmetic";
	private static final Map<UUID, AbstractHorseEntity> ACTIVE = new HashMap<>();
	private static Map<String, NbtCompound> catalog = Map.of();
	private static final Map<String, List<String>> variants = new java.util.TreeMap<>();
	private static final Map<String, String> defaults = new HashMap<>();

	@Override
	public void onInitialize() {
		ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
			entries.getDisplayStacks().removeIf(MountService::isSummoningStaff);
			entries.getSearchTabStacks().removeIf(MountService::isSummoningStaff);
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!isSummoningStaff(stack)) return TypedActionResult.pass(stack);
			if (!world.isClient) player.sendMessage(Text.literal("Use the cosmetics menu to summon mounts."), true);
			return TypedActionResult.fail(stack);
		});
		BiomeModifications.create(new Identifier("gangsmounts", "cosmetics_only")).add(
				ModificationPhase.REMOVALS, BiomeSelectors.all(), context ->
						context.getSpawnSettings().removeSpawns((group, entry) ->
								Registries.ENTITY_TYPE.getId(entry.type).getNamespace().equals("mythicmounts")));
		ServerPlayNetworking.registerGlobalReceiver(QUICK_ACTION, (server, player, handler, buffer, sender) -> {
			boolean summon = buffer.readBoolean();
			server.execute(() -> quickAction(player, summon));
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ACTIVE.clear();
			variants.clear();
			defaults.clear();
			Map<String, NbtCompound> entries = new java.util.TreeMap<>();
			for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
				if (!id.getNamespace().equals("mythicmounts")) continue;
				if (Registries.ENTITY_TYPE.get(id).create(server.getOverworld()) instanceof AbstractHorseEntity horse) {
					horse.initialize(server.getOverworld(), server.getOverworld().getLocalDifficulty(net.minecraft.util.math.BlockPos.ORIGIN),
							SpawnReason.COMMAND, null, null);
					NbtCompound nbt = new NbtCompound();
					horse.writeNbt(nbt);
					if (!(horse instanceof MountDataAccess access) || access.mountTextures().isEmpty()) {
						throw new IllegalStateException("No variants for mount: " + id);
					}
					defaults.put(id.toString(), variantId(id.toString(), access.mountTextures().get(0)));
					var textures = new java.util.TreeSet<>(access.mountTextures());
					if (id.toString().equals("mythicmounts:colelytra")) {
						for (var color : net.minecraft.util.DyeColor.values()) {
							String texture = "colelytra_mount_" + color.getName() + ".png";
							if (MountService.class.getClassLoader().getResource("assets/mythicmounts/textures/model/entity/" + texture) != null) {
								textures.add(texture);
							}
						}
					}
					List<String> rewards = new java.util.ArrayList<>();
					for (String texture : textures) {
						String reward = variantId(id.toString(), texture);
						NbtCompound initial = nbt.copy();
						initial.putString("Variant", texture);
						entries.put(reward, initial);
						rewards.add(reward);
					}
					variants.put(id.toString(), List.copyOf(rewards));
				}
			}
			if (entries.isEmpty()) throw new IllegalStateException("No rideable Mythic Mounts registered");
			catalog = entries;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			migrateLegacyUnlocks(handler.player);
			sendCatalog(handler.player);
			GangsHats.sendWardrobe(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> recall(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (AbstractHorseEntity horse : List.copyOf(ACTIVE.values())) storeAndRemove(server, horse);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (isMythicMount(entity) && !managed(entity)) {
				GangsHats.LOGGER.info("Removing unmanaged Mythic mount from cosmetics-only world: {}", id(entity));
				entity.discard();
				return;
			}
			if (managed(entity) && entity instanceof AbstractHorseEntity horse && ACTIVE.get(horse.getOwnerUuid()) != horse) {
				NbtCompound saved = Storage.get(world.getServer()).saved(horse.getOwnerUuid(), reward(horse));
				if (saved == null || saved.getUuid("UUID").equals(horse.getUuid())) storeAndRemove(world.getServer(), horse);
				else horse.discard();
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (managed(entity) && entity instanceof AbstractHorseEntity horse && ACTIVE.get(horse.getOwnerUuid()) == horse) {
				storeAndRemove(world.getServer(), horse);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (AbstractHorseEntity horse : List.copyOf(ACTIVE.values())) {
				ServerPlayerEntity owner = server.getPlayerManager().getPlayer(horse.getOwnerUuid());
				if (horse.isRemoved() || owner == null || owner.getWorld() != horse.getWorld()
						|| horse.getY() < horse.getWorld().getBottomY() + 8) storeAndRemove(server, horse);
				else {
					horse.resetLoveTicks();
					Storage.get(server).save(horse);
				}
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (isSummoningStaff(player.getStackInHand(hand))) {
				if (!world.isClient) player.sendMessage(Text.literal("Use the cosmetics menu to summon mounts."), true);
				return ActionResult.FAIL;
			}
			if (!(entity instanceof AbstractHorseEntity horse) || !managed(horse)) return ActionResult.PASS;
			if (!player.getUuid().equals(horse.getOwnerUuid()) || horse.isBreedingItem(player.getStackInHand(hand))) {
				if (!world.isClient) player.sendMessage(Text.literal("Only the owner can ride this mount; cosmetic mounts cannot breed."), true);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}

	public static boolean managed(Entity entity) { return entity.getCommandTags().contains(TAG); }
	private static boolean isSummoningStaff(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem()).equals(SUMMONING_STAFF);
	}
	public static boolean isMythicMount(Entity entity) {
		return entity instanceof AbstractHorseEntity && Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("mythicmounts");
	}
	public static List<String> ids() { return List.copyOf(catalog.keySet()); }
	public static boolean contains(String id) { return catalog.containsKey(id) || variants.containsKey(id); }
	public static String entityId(String reward) { return reward.split("__", 2)[0]; }
	public static String variantId(String type, String texture) {
		return type + "__" + texture.replace(".png", "").replace('/', '_');
	}
	public static Text name(String reward) {
		String type = entityId(reward);
		Text name = Registries.ENTITY_TYPE.get(Identifier.tryParse(type)).getName();
		List<String> skins = variants.get(type);
		return skins == null || !skins.contains(reward) ? name
				: name.copy().append(" - Variant " + (skins.indexOf(reward) + 1));
	}
	private static String reward(AbstractHorseEntity horse) {
		NbtCompound nbt = new NbtCompound();
		horse.writeNbt(nbt);
		return variantId(id(horse), nbt.getString("Variant"));
	}
	private static String legacyReward(ServerPlayerEntity player, String type) {
		NbtCompound saved = Storage.get(player.getServer()).saved(player.getUuid(), type);
		String reward = saved == null ? defaults.get(type) : variantId(type, saved.getString("Variant"));
		if (!catalog.containsKey(reward)) throw new IllegalStateException("Unknown stored mount variant: " + reward);
		return reward;
	}
	public static void migrateLegacyUnlocks(ServerPlayerEntity player) {
		var state = CosmeticUnlockState.get(player.getServer());
		var storage = Storage.get(player.getServer());
		for (String type : variants.keySet()) {
			if (!state.hasUnlock(player.getUuid(), type)) continue;
			String reward = legacyReward(player, type);
			state.unlock(player.getUuid(), reward);
			if (type.equals(storage.lastSelected(player.getUuid()))) storage.remember(player.getUuid(), reward);
		}
	}
	public static AbstractHorseEntity active(UUID owner) { return ACTIVE.get(owner); }
	private static String id(Entity entity) { return Registries.ENTITY_TYPE.getId(entity.getType()).toString(); }

	public static void sendCatalog(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, CATALOG)) return;
		var buffer = PacketByteBufs.create();
		buffer.writeVarInt(catalog.size());
		catalog.forEach((id, initial) -> {
			buffer.writeString(id);
			NbtCompound saved = savedVariant(player, id);
			buffer.writeNbt(saved == null ? initial : saved);
		});
		ServerPlayNetworking.send(player, CATALOG, buffer);
	}

	public static boolean summon(ServerPlayerEntity player, String reward) {
		var server = player.getServer();
		var state = CosmeticUnlockState.get(server);
		migrateLegacyUnlocks(player);
		if (variants.containsKey(reward)) reward = legacyReward(player, reward);
		if (!contains(reward) || !CosmeticAccess.canUse(player)
				|| !(state.hasUnlock(player.getUuid(), reward) || CosmeticAccess.isUnlockAll())) {
			player.sendMessage(Text.literal("That mount is locked or unavailable."), false);
			return false;
		}
		if (ACTIVE.containsKey(player.getUuid())) {
			player.sendMessage(Text.literal("Recall your active mount before summoning another."), false);
			return false;
		}
		var world = player.getServerWorld();
		Entity entity = Registries.ENTITY_TYPE.get(Identifier.tryParse(entityId(reward))).create(world);
		if (!(entity instanceof AbstractHorseEntity horse)) throw new IllegalStateException("Not a rideable mount: " + reward);
		NbtCompound saved = savedVariant(player, reward);
		NbtCompound nbt = (saved == null ? catalog.get(reward) : saved).copy();
		nbt.putBoolean("IsDomesticated", true);
		horse.readNbt(nbt);
		horse.setUuid(UUID.randomUUID());
		horse.setTame(true);
		horse.setOwnerUuid(player.getUuid());
		horse.setBreedingAge(0);
		if (saved == null) horse.saddle(null);
		horse.addCommandTag(TAG);
		horse.setInvulnerable(true);
		horse.setPersistent();
		horse.resetLoveTicks();
		horse.setVelocity(0, 0, 0);
		horse.fallDistance = 0;
		boolean safe = false;
		for (int x = -4; x <= 4 && !safe; x++) {
			for (int z = -4; z <= 4 && !safe; z++) {
				if (Math.abs(x) + Math.abs(z) < 2) continue;
				var pos = player.getBlockPos().add(x, 0, z);
				if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)
						|| !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
				horse.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
				safe = world.isSpaceEmpty(horse) && !world.containsFluid(horse.getBoundingBox());
			}

		}
		if (!safe) {
			player.sendMessage(Text.literal("Move to open, solid ground; no safe space nearby for this mount."), false);
			return false;
		}
		ACTIVE.put(player.getUuid(), horse);
		if (!world.spawnEntity(horse)) {
			ACTIVE.remove(player.getUuid());
			player.sendMessage(Text.literal("Mount could not be spawned."), false);
			return false;
		}
		state.select(player.getUuid(), SLOT, reward);
		Storage.get(server).save(horse);
		Storage.get(server).remember(player.getUuid(), reward);
		sendCatalog(player);
		player.sendMessage(Text.literal("Summoned: ").append(name(reward)), false);
		return true;
	}

	private static NbtCompound savedVariant(ServerPlayerEntity player, String reward) {
		var storage = Storage.get(player.getServer());
		NbtCompound saved = storage.saved(player.getUuid(), reward);
		if (saved != null) return saved;
		NbtCompound legacy = storage.saved(player.getUuid(), entityId(reward));
		return legacy != null && variantId(entityId(reward), legacy.getString("Variant")).equals(reward) ? legacy : null;
	}

	public static String resolvedReward(ServerPlayerEntity player, String reward) {
		return variants.containsKey(reward) ? legacyReward(player, reward) : reward;
	}

	public static void quickAction(ServerPlayerEntity player, boolean summon) {
		if (summon) {
			String last = Storage.get(player.getServer()).lastSelected(player.getUuid());
			if (last == null) player.sendMessage(Text.literal("Select a mount in the cosmetics menu first."), true);
			else summon(player, last);
		} else if (ACTIVE.containsKey(player.getUuid())) {
			recall(player);
		} else {
			player.sendMessage(Text.literal("You have no active mount to recall."), true);
		}
	}

	public static void recall(ServerPlayerEntity player) {
		AbstractHorseEntity horse = ACTIVE.get(player.getUuid());
		if (horse != null) {
			storeAndRemove(player.getServer(), horse);
			sendCatalog(player);
			player.sendMessage(Text.literal("Mount recalled and stored."), false);
		}
		CosmeticUnlockState.get(player.getServer()).clear(player.getUuid(), SLOT);
		GangsHats.sendWardrobe(player);
	}

	private static void storeAndRemove(MinecraftServer server, AbstractHorseEntity horse) {
		if (horse.getOwnerUuid() == null) throw new IllegalStateException("Cosmetic mount has no owner: " + horse.getUuid());
		horse.removeAllPassengers();
		Storage.get(server).save(horse);
		ACTIVE.remove(horse.getOwnerUuid(), horse);
		CosmeticUnlockState.get(server).clear(horse.getOwnerUuid(), SLOT);
		horse.discard();
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(horse.getOwnerUuid());
		if (owner != null) {
			GangsHats.sendWardrobe(owner);
			sendCatalog(owner);
		}
	}

	public static final class Storage extends PersistentState {
		private final Map<UUID, Map<String, NbtCompound>> stored = new HashMap<>();
		private final Map<UUID, String> lastSelections = new HashMap<>();
		public static Storage get(MinecraftServer server) {
			return server.getOverworld().getPersistentStateManager().getOrCreate(Storage::fromNbt, Storage::new, "gangsmounts_storage");
		}
		public void save(AbstractHorseEntity horse) {
			NbtCompound nbt = new NbtCompound();
			horse.writeNbt(nbt);
			nbt.remove("Passengers");
			stored.computeIfAbsent(horse.getOwnerUuid(), ignored -> new HashMap<>()).put(reward(horse), nbt);
			markDirty();
		}
		public NbtCompound saved(UUID owner, String id) { return stored.getOrDefault(owner, Map.of()).get(id); }
		public String lastSelected(UUID owner) { return lastSelections.get(owner); }
		public void remember(UUID owner, String id) {
			lastSelections.put(owner, id);
			markDirty();
		}
		public static Storage fromNbt(NbtCompound nbt) {
			Storage state = new Storage();
			NbtList players = nbt.getList("Players", NbtCompound.COMPOUND_TYPE);
			for (int i = 0; i < players.size(); i++) {
				NbtCompound player = players.getCompound(i);
				Map<String, NbtCompound> mounts = new HashMap<>();
				NbtCompound values = player.getCompound("Mounts");
				for (String id : values.getKeys()) mounts.put(id, values.getCompound(id));
				state.stored.put(player.getUuid("Owner"), mounts);
				String last = player.getString("LastSelected");
				if (!last.isEmpty()) state.lastSelections.put(player.getUuid("Owner"), last);
			}
			return state;
		}
		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			NbtList players = new NbtList();
			stored.forEach((owner, mounts) -> {
				NbtCompound player = new NbtCompound();
				player.putUuid("Owner", owner);
				String last = lastSelections.get(owner);
				if (last != null) player.putString("LastSelected", last);
				NbtCompound values = new NbtCompound();
				mounts.forEach((id, value) -> values.put(id, value.copy()));
				player.put("Mounts", values);
				players.add(player);
			});
			nbt.put("Players", players);
			return nbt;
		}
	}
}
