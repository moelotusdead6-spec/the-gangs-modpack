package com.gangs.mounts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
			Map<String, NbtCompound> entries = new java.util.TreeMap<>();
			for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
				if (!id.getNamespace().equals("mythicmounts")) continue;
				if (Registries.ENTITY_TYPE.get(id).create(server.getOverworld()) instanceof AbstractHorseEntity horse) {
					horse.initialize(server.getOverworld(), server.getOverworld().getLocalDifficulty(net.minecraft.util.math.BlockPos.ORIGIN),
							SpawnReason.COMMAND, null, null);
					NbtCompound nbt = new NbtCompound();
					horse.writeNbt(nbt);
					entries.put(id.toString(), nbt);
				}
			}
			if (entries.isEmpty()) throw new IllegalStateException("No rideable Mythic Mounts registered");
			catalog = entries;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendCatalog(handler.player));
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
				NbtCompound saved = Storage.get(world.getServer()).saved(horse.getOwnerUuid(), id(horse));
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
	public static boolean contains(String id) { return catalog.containsKey(id); }
	public static Text name(String id) { return Registries.ENTITY_TYPE.get(Identifier.tryParse(id)).getName(); }
	public static AbstractHorseEntity active(UUID owner) { return ACTIVE.get(owner); }
	private static String id(Entity entity) { return Registries.ENTITY_TYPE.getId(entity.getType()).toString(); }

	public static void sendCatalog(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, CATALOG)) return;
		var buffer = PacketByteBufs.create();
		buffer.writeVarInt(catalog.size());
		catalog.forEach((id, initial) -> {
			buffer.writeString(id);
			NbtCompound saved = Storage.get(player.getServer()).saved(player.getUuid(), id);
			buffer.writeNbt(saved == null ? initial : saved);
		});
		ServerPlayNetworking.send(player, CATALOG, buffer);
	}

	public static boolean summon(ServerPlayerEntity player, String reward) {
		var server = player.getServer();
		var state = CosmeticUnlockState.get(server);
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
		Entity entity = Registries.ENTITY_TYPE.get(Identifier.tryParse(reward)).create(world);
		if (!(entity instanceof AbstractHorseEntity horse)) throw new IllegalStateException("Not a rideable mount: " + reward);
		NbtCompound saved = Storage.get(server).saved(player.getUuid(), reward);
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
			stored.computeIfAbsent(horse.getOwnerUuid(), ignored -> new HashMap<>()).put(id(horse), nbt);
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
