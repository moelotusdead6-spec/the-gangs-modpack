package com.gangs.gravescompat;

import com.mojang.authlib.GameProfile;
import eu.pb4.graves.event.GraveValidPosCheckEvent;
import eu.pb4.graves.grave.GraveManager;
import eu.pb4.graves.other.GraveUtils;
import eu.pb4.graves.other.PlayerAdditions;
import eu.pb4.graves.registry.GraveGameRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;

public final class GraveSmokeTest implements DedicatedServerModInitializer {
    private record Fixture(ServerPlayerEntity player, BlockPos death, List<NbtCompound> stacks,
                           NbtCompound externalContents) {}
    private final List<Fixture> fixtures = new ArrayList<>();
    private boolean pending;
    private int ticks;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pending = true);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!pending) return;
            // Fresh upstream managers initialize expiry/protection on their 20-tick refresh.
            if (++ticks == 22) createFixtures(server);
            if (ticks < 25) return;
            pending = false;
            try {
                verify(server);
                verifyBackpackBackup();
                Files.writeString(Path.of("grave-smoke-passed.txt"),
                    "PASS: ordinary floating grave unchanged; void/above-limit graves retain X/Z "
                    + "and safe legal Y; empty column remains unsupported; exact nested item NBT "
                    + "including armour/offhand captured and backed up; nested external backpack "
                    + "contents preserved with missing-data reporting; upstream protection veto "
                    + "preserved; last-grave ID set.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Grave smoke verification failed", failure);
            }
            server.stop(false);
        });
    }

    private void createFixtures(MinecraftServer server) {
        require(GraveManager.INSTANCE.getBreakingTime() == GraveGameRules.getBreakingTime(server)
            && GraveManager.INSTANCE.getProtectionTime() == GraveGameRules.getProtectionTime(server),
            "upstream grave timers must be initialized before the death fixtures");
        var world = server.getOverworld();
        var previous = GraveManager.INSTANCE.getByLocation(world, new BlockPos(32, 100, 32));
        if (previous != null) GraveManager.INSTANCE.remove(previous);
        world.setBlockState(new BlockPos(32, 100, 32), Blocks.AIR.getDefaultState(), 2);
        create(server, world, new BlockPos(32, 100, 32));
        create(server, world, new BlockPos(48, world.getBottomY() - 80, 48));
        create(server, world, new BlockPos(64, world.getTopY() + 200, 64));
        for (int y = world.getBottomY(); y < world.getTopY(); y++) {
            world.setBlockState(new BlockPos(80, y, 80), Blocks.AIR.getDefaultState(), 2);
        }
        create(server, world, new BlockPos(80, world.getBottomY() - 80, 80));
        var denied = player(server, world, "Denied");
        GraveValidPosCheckEvent.EVENT.register((p, w, pos) -> p == denied
            ? GraveUtils.BlockResult.BLOCK_CLAIM : GraveUtils.BlockResult.ALLOW);
        require(!GraveUtils.findGravePosition(denied, world,
            new BlockPos(96, world.getTopY() + 200, 96), 0, false).result().canCreate(),
            "upstream protection veto must not be bypassed");
    }

    private void create(MinecraftServer server, ServerWorld world, BlockPos death) {
        var player = player(server, world, "GraveTest");
        player.setPosition(death.getX() + 0.5, death.getY(), death.getZ() + 0.5);
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setCustomName(Text.literal("Exact recovery"));
        stack.getOrCreateNbt().putUuid("StorageUUID", UUID.randomUUID());
        NbtCompound nested = new NbtCompound();
        nested.putString("Contents", "preserve nested backpack-style data");
        stack.getOrCreateNbt().put("Nested", nested);
        player.getInventory().setStack(0, stack);
        var chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chestplate.setDamage(17);
        var shield = new ItemStack(Items.SHIELD);
        shield.setCustomName(Text.literal("Offhand recovery"));
        player.getInventory().setStack(38, chestplate);
        player.getInventory().setStack(40, shield);
        var expected = new ArrayList<>(List.of(stack.writeNbt(new NbtCompound()),
            chestplate.writeNbt(new NbtCompound()), shield.writeNbt(new NbtCompound())));
        NbtCompound external = null;
        if (FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks")) {
            UUID uuid = UUID.randomUUID();
            var bag = new ItemStack(Registries.ITEM.get(
                new Identifier("sophisticatedbackpacks", "netherite_backpack")));
            require(!bag.isEmpty(), "real backpack item must be registered");
            bag.getOrCreateNbt().putUuid("contentsUuid", uuid);
            bag.getOrCreateNbt().putInt("inventorySlots", 120);
            bag.getOrCreateNbt().putInt("upgradeSlots", 7);
            NbtCompound contents = new NbtCompound();
            NbtCompound inventory = new NbtCompound();
            inventory.putInt("Size", 120);
            NbtList items = new NbtList();
            NbtCompound stored = stack.writeNbt(new NbtCompound());
            stored.putInt("Slot", 119);
            stored.putInt("realCount", 2048);
            items.add(stored);
            inventory.put("Items", items);
            contents.put("inventory", inventory);
            BackpackStorage.get().setBackpackContents(uuid, contents);
            player.getInventory().setStack(1, bag);
            expected.add(bag.writeNbt(new NbtCompound()));
            external = new NbtCompound();
            external.putUuid("uuid", uuid);
            external.put("contents", contents.copy());
        }
        player.totalExperience = 42;
        player.experienceLevel = 3;
        player.onDeath(world.getDamageSources().wither());
        fixtures.add(new Fixture(player, death, expected, external));
    }

    private void verify(MinecraftServer server) throws IOException {
        for (var fixture : fixtures) {
            var graves = GraveManager.INSTANCE.getByPlayer(fixture.player());
            require(graves.size() == 1, "exactly one real grave must be created at "
                + fixture.death() + "; found " + graves.size() + "; queued="
                + eu.pb4.graves.GravesMod.DO_ON_NEXT_TICK.size() + "; state="
                + fixture.player().getServerWorld().getBlockState(fixture.death()) + "; entity="
                + fixture.player().getServerWorld().getBlockEntity(fixture.death()));
            var grave = graves.iterator().next();
            BlockPos pos = grave.getLocation().blockPos();
            var world = fixture.player().getServerWorld();
            require(pos.getX() == fixture.death().getX() && pos.getZ() == fixture.death().getZ(),
                "X/Z must be unchanged");
            require(pos.getY() > world.getBottomY() && pos.getY() <= world.getTopY() - 3,
                "grave must have legal Y and headroom");
            if (fixture.death().getY() == 100) require(pos.equals(fixture.death()),
                "normal floating placement must be unchanged");
            if (pos.getX() == 80) {
                require(pos.getY() == 64 && world.getBlockState(pos.down()).isAir(),
                    "empty-column grave must float at Y64 without a platform");
            }
            var captured = grave.getItems().stream()
                .map(item -> item.stack().writeNbt(new NbtCompound())).toList();
            require(captured.size() == fixture.stacks().size()
                && captured.containsAll(fixture.stacks()),
                "grave must preserve exact item NBT");
            require(fixture.player().getInventory().isEmpty(),
                "captured inventory must not remain duplicated on player");
            require(((PlayerAdditions)fixture.player()).graves$lastGrave() == grave.getId(),
                "respawn compass must receive the actual relocated grave ID");
            Path directory = server.getSavePath(WorldSavePath.ROOT).resolve("gangs-death-backups")
                .resolve(fixture.player().getUuidAsString());
            try (var files = Files.list(directory)) {
                Path backup = files.filter(p -> p.toString().endsWith(".dat")).findFirst().orElseThrow();
                var envelope = NbtIo.readCompressed(backup.toFile());
                var data = envelope.getCompound("Player");
                require(data.getInt("XpTotal") == 42, "pre-drop XP must be backed up");
                var saved = new ArrayList<NbtCompound>();
                for (var element : data.getList("Inventory", 10)) {
                    var item = ((NbtCompound) element).copy();
                    item.remove("Slot");
                    saved.add(item);
                }
                require(saved.size() == fixture.stacks().size()
                    && saved.containsAll(fixture.stacks()),
                    "pre-grave backup must preserve inventory, armour and offhand NBT");
                if (fixture.externalContents() != null) {
                    require(envelope.getBoolean("BackpackContentsComplete")
                        && envelope.getList("BackpackContents", 10).size() == 1
                        && envelope.getList("BackpackContents", 10).getCompound(0)
                            .equals(fixture.externalContents()),
                        "actual death hook must copy exact live Sophisticated Backpacks storage");
                }
            }
        }
    }

    private static void verifyBackpackBackup() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();
        NbtCompound player = new NbtCompound();
        player.put("Inventory", backpack(outer));
        player.put("cardinal_components", backpack(outer));
        NbtCompound backup = new NbtCompound();
        backup.put("Player", player);
        NbtCompound storage = new NbtCompound();
        NbtList entries = new NbtList();
        for (UUID uuid : List.of(outer, inner)) {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("uuid", uuid);
            NbtCompound contents = new NbtCompound();
            contents.put("NestedBackpack", backpack(uuid.equals(outer) ? inner : outer));
            var item = new ItemStack(Items.DIAMOND_SWORD);
            item.setCustomName(Text.literal("External exact contents"));
            NbtCompound savedItem = item.writeNbt(new NbtCompound());
            savedItem.putInt("realCount", 2048);
            contents.put("StoredItem", savedItem);
            entry.put("contents", contents);
            entries.add(entry);
        }
        storage.put("backpackContents", entries);
        require(BackpackBackup.addTo(backup, storage), "existing backpack contents must be complete");
        require(backup.getList("BackpackContents", 10).equals(entries),
            "nested/cyclic UUID references must be captured once with exact typed NBT and realCount");
        entries.getCompound(0).getCompound("contents").getCompound("StoredItem").putInt("realCount", 1);
        require(backup.getList("BackpackContents", 10).getCompound(0)
            .getCompound("contents").getCompound("StoredItem").getInt("realCount") == 2048,
            "archived backpack contents must not share mutable live NBT");
        player.put("Offhand", backpack(UUID.randomUUID()));
        require(!BackpackBackup.addTo(backup, storage)
            && !backup.getBoolean("BackpackContentsComplete")
            && backup.getList("MissingBackpackContents", 10).size() == 1,
            "missing external storage must be explicitly recorded, not silently replaced");
    }

    private static NbtCompound backpack(UUID uuid) {
        NbtCompound item = new NbtCompound();
        item.putString("id", "sophisticatedbackpacks:netherite_backpack");
        NbtCompound tag = new NbtCompound();
        tag.putUuid("contentsUuid", uuid);
        item.put("tag", tag);
        return item;
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name) {
        var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name)) {
            @Override public void sendMessage(Text message) {}
        };
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
