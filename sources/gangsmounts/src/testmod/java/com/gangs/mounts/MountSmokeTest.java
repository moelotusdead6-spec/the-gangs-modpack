package com.gangs.mounts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.thegangs.gangshats.CosmeticCatalog;
import com.thegangs.gangshats.CosmeticItems;
import com.thegangs.gangshats.CosmeticRewards;
import com.thegangs.gangshats.CosmeticTokenState;
import com.thegangs.gangshats.CosmeticUnlockState;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class MountSmokeTest implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			verify(server);
			try {
				Files.writeString(Path.of("mount-smoke-passed.txt"),
						"PASS with unmodified Gangs Cosmetics 2.0.2: original model/halo/weapon catalog retained; "
						+ "mount keys and authenticated duplicate vouchers; locking; one active; all real mounts "
						+ "tamed/saddled/domesticated/rideable; all registered damage types blocked even with "
						+ "vanilla invulnerability disabled; direct-health/void/kill immunity; no breeding; "
						+ "NBT recall persistence; remembered summon/recall shortcuts; logout/dimension recall; "
						+ "no biome or unmanaged Mythic spawns; ordinary horses unaffected.\n");
			} catch (java.io.IOException failure) { throw new IllegalStateException("Cannot write smoke result", failure); }
			server.stop(false);
		});
	}

	private void verify(MinecraftServer server) {
		require(CosmeticCatalog.entries().size() == 20, "Original model catalog changed");
		var staffId = new net.minecraft.util.Identifier("mythicmounts", "summoning_staff");
		require(server.getRecipeManager().get(staffId).isEmpty(), "Summoning Staff recipe remains enabled");
		try {
			Object catalog = Class.forName("com.gangs.gangshop.GangShopMod").getField("CATALOG").get(null);
			var getEntry = catalog.getClass().getMethod("getEntry", net.minecraft.util.Identifier.class);
			int checked = 0;
			for (var item : net.minecraft.registry.Registries.ITEM) {
				var id = net.minecraft.registry.Registries.ITEM.getId(item);
				if (!id.getNamespace().equals("mythicmounts")) continue;
				require(getEntry.invoke(catalog, id) == null, "Mount item leaked into shop: " + id);
				checked++;
			}
			require(checked > 0, "No Mythic Mounts items checked against the shop");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Cannot verify Gang Shop catalog", failure);
		}
		for (var item : CosmeticItems.all()) require(CosmeticRewards.allIds().contains(net.minecraft.registry.Registries.ITEM.getId(item).toString()), "Original reward missing");
		require(CosmeticRewards.allIds().containsAll(MountService.ids()), "Mounts not in cosmetic keys");
		for (var biome : server.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME)) {
			for (var group : net.minecraft.entity.SpawnGroup.values()) {
				for (var entry : biome.getSpawnSettings().getSpawnEntries(group).getEntries()) {
					require(!net.minecraft.registry.Registries.ENTITY_TYPE.getId(entry.type).getNamespace().equals("mythicmounts"),
							"Mythic mount remains in biome spawn pool");
				}
			}
		}
		var owner = new Fixture(server, "MountOwner");
		var recipient = new Fixture(server, "MountRecipient");
		var world = server.getOverworld();
		var origin = new BlockPos(0, 100, 0);
		world.getChunk(origin);
		for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
			world.setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState());
			for (int y = 0; y <= 6; y++) world.setBlockState(origin.add(x, y, z), Blocks.AIR.getDefaultState());
		}
		owner.refreshPositionAndAngles(0.5, 100, 0.5, 0, 0);
		MountService.quickAction(owner, true);
		require(MountService.active(owner.getUuid()) == null, "Shortcut summoned without a GUI selection");
		String first = MountService.ids().get(0);
		require(!MountService.summon(owner, first), "Locked mount summoned");
		ItemStack voucher = CosmeticTokenState.get(server).issueVoucher(first);
		owner.setStackInHand(Hand.MAIN_HAND, voucher);
		require(UseItemCallback.EVENT.invoker().interact(owner, world, Hand.MAIN_HAND).getResult().isAccepted(), "Mount voucher rejected");
		require(voucher.isEmpty() && CosmeticUnlockState.get(server).hasUnlock(owner.getUuid(), first), "Mount voucher did not unlock");
		ItemStack duplicate = CosmeticTokenState.get(server).issueVoucher(first);
		ItemStack copied = duplicate.copy();
		owner.setStackInHand(Hand.MAIN_HAND, duplicate);
		require(UseItemCallback.EVENT.invoker().interact(owner, world, Hand.MAIN_HAND).getResult() == ActionResult.FAIL && duplicate.getCount() == 1, "Owned mount voucher consumed");
		recipient.setStackInHand(Hand.MAIN_HAND, duplicate);
		require(UseItemCallback.EVENT.invoker().interact(recipient, world, Hand.MAIN_HAND).getResult().isAccepted(), "Recipient cannot redeem mount voucher");
		require(CosmeticUnlockState.get(server).hasUnlock(recipient.getUuid(), first), "Recipient ownership missing");
		var third = new Fixture(server, "MountThird");
		third.setStackInHand(Hand.MAIN_HAND, copied);
		require(UseItemCallback.EVENT.invoker().interact(third, world, Hand.MAIN_HAND).getResult() == ActionResult.FAIL, "Copied token redeemed twice");
		third.setStackInHand(Hand.MAIN_HAND, new ItemStack(CosmeticItems.COSMETIC_VOUCHER));
		require(UseItemCallback.EVENT.invoker().interact(third, world, Hand.MAIN_HAND).getResult() == ActionResult.FAIL, "Unissued token accepted");
		var keyRecipient = new Fixture(server, "KeyRecipient");
		int initialMountUnlocks = (int) MountService.ids().stream().filter(id -> CosmeticUnlockState.get(server).hasUnlock(owner.getUuid(), id)).count();
		boolean mountDuplicate = false;
		for (int i = 0; i < 256; i++) {
			ItemStack key = CosmeticTokenState.get(server).issueKey();
			owner.setStackInHand(Hand.MAIN_HAND, key);
			owner.payout = ItemStack.EMPTY;
			require(UseItemCallback.EVENT.invoker().interact(owner, world, Hand.MAIN_HAND).getResult().isAccepted() && key.isEmpty(),
					"Authenticated cosmetic key failed to redeem");
			var payoutNbt = owner.payout.getNbt();
			Object ledger = CosmeticTokenState.get(server);
			if (payoutNbt != null && ledger instanceof com.gangs.mounts.mixin.TokenLedgerAccess access) {
				Object record = access.mountTokens().get(payoutNbt.getUuid("gangscosmetics_token_id"));
				if (!mountDuplicate && record instanceof com.gangs.mounts.mixin.TokenRecordAccess token && MountService.contains(token.mountReward())) {
					keyRecipient.setStackInHand(Hand.MAIN_HAND, owner.payout);
					require(UseItemCallback.EVENT.invoker().interact(keyRecipient, world, Hand.MAIN_HAND).getResult().isAccepted(),
							"Duplicate cosmetic-key mount voucher cannot transfer");
					require(CosmeticUnlockState.get(server).hasUnlock(keyRecipient.getUuid(), token.mountReward()),
							"Key duplicate voucher unlocked wrong mount");
					mountDuplicate = true;
				}
			}
		}
		require(mountDuplicate, "No mount duplicate voucher produced by cosmetic keys");
		require(MountService.ids().stream().filter(id -> CosmeticUnlockState.get(server).hasUnlock(owner.getUuid(), id)).count() > initialMountUnlocks,
				"Cosmetic keys never unlocked a mount");
		var wild = net.minecraft.entity.EntityType.HORSE.create(world);
		require(world.spawnEntity(wild), "Ordinary horse spawning was blocked");
		wild.lovePlayer(owner);
		require(wild.isInLove(), "Ordinary horse breeding disabled");
		float wildHealth = wild.getHealth();
		require(wild.damage(world.getDamageSources().generic(), 1) && wild.getHealth() < wildHealth,
				"Ordinary horse damage was blocked");
		wild.kill();
		require(!wild.isAlive(), "Ordinary horse death disabled");
		for (String id : MountService.ids()) {
			var unmanaged = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.tryParse(id)).create(world);
			require(!world.spawnEntity(unmanaged), "Unmanaged Mythic mount spawned: " + id);
			net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(unmanaged, world);
			require(unmanaged.isRemoved(), "Loaded unmanaged Mythic mount was retained: " + id);
			CosmeticUnlockState.get(server).unlock(owner.getUuid(), id);
			require(MountService.summon(owner, id), "Summon failed: " + id);
			var horse = MountService.active(owner.getUuid());
			require(horse.isTame() && horse.isSaddled() && owner.getUuid().equals(horse.getOwnerUuid()), "Mount setup incomplete");
			require(!MountService.summon(owner, id), "Second active mount spawned");
			require(owner.startRiding(horse, true), "Mount not rideable");
			float health = horse.getHealth();
			horse.setInvulnerable(false);
			for (var damage : world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).streamEntries().toList()) {
				require(!horse.damage(new net.minecraft.entity.damage.DamageSource(damage), 10000),
						"Registered damage source affected cosmetic mount: " + damage.getKey().orElseThrow().getValue());
				require(horse.getHealth() == health, "Damage changed mount health");
			}
			horse.setHealth(health - 1);
			horse.setHealth(Float.NaN);
			require(horse.getHealth() == health, "Direct health reduction bypassed protection");
			horse.setInvulnerable(true);
			horse.damage(world.getDamageSources().generic(), 10000);
			horse.damage(world.getDamageSources().outOfWorld(), 10000);
			horse.kill();
			horse.setHealth(0);
			require(horse.isAlive() && horse.getHealth() == health, "Mount can die");
			horse.lovePlayer(owner);
			horse.setLoveTicks(600);
			require(!horse.isInLove(), "Mount can breed");
			horse.setCustomName(Text.literal("Stored Mount"));
			NbtCompound before = new NbtCompound();
			horse.writeNbt(before);
			require(before.getBoolean("IsDomesticated"), "Mount not domesticated");
			MountService.recall(owner);
			require(horse.isRemoved() && !owner.hasVehicle(), "Recall failed");
			var storage = MountService.Storage.get(server);
			var saved = storage.saved(owner.getUuid(), id);
			net.minecraft.world.PersistentState persistent = storage;
			require(MountService.Storage.fromNbt(persistent.writeNbt(new NbtCompound())).saved(owner.getUuid(), id).equals(saved), "Storage round trip lost NBT");
			require(MountService.summon(owner, id), "Stored mount cannot summon");
			var restored = MountService.active(owner.getUuid());
			NbtCompound after = new NbtCompound();
			restored.writeNbt(after);
			require(restored.getCustomName().getString().equals("Stored Mount") && before.getString("Variant").equals(after.getString("Variant")), "Stored appearance changed");
			require(attributes(before).equals(attributes(after)), "Stored stats changed");
			require(before.getCompound("SaddleItem").equals(after.getCompound("SaddleItem")) && restored.isSaddled(),
					"Stored saddle equipment changed");
			MountService.recall(owner);
		}
		String last = MountService.Storage.get(server).lastSelected(owner.getUuid());
		require(last.equals(MountService.ids().get(MountService.ids().size() - 1)), "Last mount selection not retained after recall");
		MountService.quickAction(owner, true);
		require(MountService.active(owner.getUuid()) != null
				&& net.minecraft.registry.Registries.ENTITY_TYPE.getId(MountService.active(owner.getUuid()).getType()).toString().equals(last),
				"Shortcut did not summon last selected mount");
		MountService.quickAction(owner, false);
		require(MountService.active(owner.getUuid()) == null, "Recall shortcut failed");
		net.minecraft.world.PersistentState shortcutStorage = MountService.Storage.get(server);
		require(last.equals(MountService.Storage.fromNbt(shortcutStorage.writeNbt(new NbtCompound())).lastSelected(owner.getUuid())),
				"Last selected mount was not persisted");
		require(MountService.summon(owner, first), "Logout setup failed");
		ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(owner.networkHandler, server);
		require(MountService.active(owner.getUuid()) == null, "Logout did not recall");
		require(MountService.summon(owner, first), "Travel setup failed");
		owner.teleport(server.getWorld(net.minecraft.world.World.NETHER), 0, 100, 0, 0, 0);
		require(MountService.active(owner.getUuid()) == null, "Travel did not recall");
		require(first.equals(MountService.Storage.get(server).lastSelected(owner.getUuid())), "Travel erased shortcut selection");
	}

	private static java.util.Map<String, NbtCompound> attributes(NbtCompound nbt) {
		var values = new java.util.HashMap<String, NbtCompound>();
		var list = nbt.getList("Attributes", 10);
		for (int i = 0; i < list.size(); i++) values.put(list.getCompound(i).getString("Name"), list.getCompound(i));
		return values;
	}
	private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
	private static final class Fixture extends ServerPlayerEntity {
		ItemStack payout = ItemStack.EMPTY;
		Fixture(MinecraftServer server, String name) {
			super(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), name));
			networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), this);
		}
		@Override public void sendMessage(Text message, boolean overlay) { }
		@Override public boolean giveItemStack(ItemStack stack) { payout = stack.copy(); return true; }
	}
}
