package com.gangs.invhistory;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.RegionFile;

public final class HistorySmokeTest implements DedicatedServerModInitializer {
    private static ShutdownProbe shutdownProbe;
    private static boolean injectedFailure;
    private boolean pending;
    private int ticks;
    private String result;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pending = true);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!pending || ++ticks < 5) return;
            pending = false;
            try {
                String backpacks = run(server);
                result = "PASS: join/leave callbacks and actual dimension/death hooks; pre-drop death inventory; exact main/hotbar/"
                    + "armour/offhand/ender decoding; 7-day pruning; permission-gated list/view commands; "
                    + "read-only GUI navigation and blocked item transfers for level-4 operators" + backpacks + ".\n";
                if (Boolean.getBoolean("gangsinvhistory.smoke.failDisconnect")) {
                    shutdownProbe = ShutdownProbe.prepare(server);
                }
            } catch (Exception failure) {
                result = null;
                throw new IllegalStateException("Inventory history smoke failed", failure);
            }
            server.stop(false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (result == null) return;
            try {
                if (shutdownProbe != null) result += shutdownProbe.verify();
                Files.writeString(Path.of("history-smoke-passed.txt"), result);
            } catch (Exception failure) {
                throw new IllegalStateException("Inventory history shutdown checks failed", failure);
            }
        });
    }

    public static void injectShutdownFailure() {
        if (shutdownProbe == null) return;
        injectedFailure = true;
        throw new RejectedExecutionException("simulated LuckPerms shutdown failure");
    }

    private record ShutdownProbe(Path root, BlockPos pos, String marker, UUID backpack) {
        static ShutdownProbe prepare(MinecraftServer server) {
            // Write fresh data on the final tick, with no intervening manual save.
            ServerWorld world = server.getOverworld();
            BlockPos pos = world.getSpawnPos().up(4);
            String marker = "shutdown-smoke-" + UUID.randomUUID();
            world.setBlockState(pos, Blocks.CHEST.getDefaultState());
            var chest = (LockableContainerBlockEntity) world.getBlockEntity(pos);
            require(chest != null, "could not place shutdown test chest");
            chest.setCustomName(Text.literal(marker));
            chest.markDirty();
            UUID backpack = InvHistoryMod.BACKPACKS ? Backpacks.prepareShutdown() : null;
            return new ShutdownProbe(server.getSavePath(WorldSavePath.ROOT), pos, marker, backpack);
        }

        String verify() throws Exception {
            require(injectedFailure, "shutdown disconnect fault did not run");
            ChunkPos chunk = new ChunkPos(pos);
            Path regionDir = root.resolve("region");
            Path regionFile = regionDir.resolve("r." + chunk.getRegionX() + "." + chunk.getRegionZ() + ".mca");
            require(Files.isRegularFile(regionFile), "shutdown did not write the test chunk region");
            try (var region = new RegionFile(regionFile, regionDir, false);
                 var stream = region.getChunkInputStream(chunk)) {
                require(stream != null, "shutdown did not write the test chunk");
                var entities = NbtIo.read(stream).getList("block_entities", 10);
                boolean found = false;
                for (int i = 0; i < entities.size(); i++) {
                    var chest = entities.getCompound(i);
                    if (chest.getInt("x") == pos.getX() && chest.getInt("y") == pos.getY()
                        && chest.getInt("z") == pos.getZ() && chest.getString("CustomName").contains(marker)) {
                        found = true;
                        break;
                    }
                }
                require(found, "shutdown did not persist the fresh chunk marker");
            }
            if (backpack != null) Backpacks.verifyShutdown(root, backpack);
            return "PASS: injected RejectedExecutionException; fresh chunk marker persisted"
                + (backpack != null ? "; fresh Sophisticated Backpacks storage persisted" : "") + ".\n";
        }
    }

    private static String run(MinecraftServer server) throws Exception {
        var store = InvHistoryMod.store();
        require(store != null, "store must start with the server");
        require(store.retention().equals(Duration.ofDays(7)), "default retention must be 7 days");
        var overworld = server.getOverworld();
        var subject = player(server, overworld, "HistoryTest");
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setCustomName(Text.literal("Exact history"));
        sword.setDamage(9);
        subject.getInventory().setStack(0, sword);
        subject.getInventory().setStack(20, new ItemStack(Items.DIRT, 32));
        subject.getInventory().setStack(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
        subject.getInventory().setStack(40, new ItemStack(Items.SHIELD));
        subject.getEnderChestInventory().setStack(3, new ItemStack(Items.GOLDEN_APPLE, 5));
        Backpacks bags = InvHistoryMod.BACKPACKS ? Backpacks.give(subject) : null;

        ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(subject.networkHandler, null, server);
        ServerWorld nether = server.getWorld(World.NETHER);
        subject.teleport(nether, 0.5, 80, 0.5, 0, 0);
        require(subject.getWorld() == nether, "player must actually teleport to the Nether");
        store.flush();
        var afterTeleport = store.list(subject.getUuid());
        require(afterTeleport.size() == 2 && afterTeleport.get(0).event() == SnapshotEvent.DIMENSION
            && afterTeleport.get(1).event() == SnapshotEvent.JOIN,
            "actual cross-world teleport must capture exactly one dimension snapshot");
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(subject.networkHandler, server);
        subject.onDeath(subject.getDamageSources().wither());
        require(subject.getInventory().getStack(0).isEmpty(), "vanilla death must still drop the inventory");
        store.flush();

        var entries = store.list(subject.getUuid());
        require(entries.size() == 4, "four snapshots expected, found " + entries.size());
        require(entries.get(0).event() == SnapshotEvent.DEATH && entries.get(1).event() == SnapshotEvent.LEAVE
            && entries.get(2).event() == SnapshotEvent.DIMENSION && entries.get(3).event() == SnapshotEvent.JOIN,
            "snapshots must be listed newest first: " + entries);
        for (var entry : entries) {
            NbtCompound data = NbtIo.readCompressed(entry.file().toFile());
            require(data.getLong("SnapshotId") == entry.id() && data.getString("Event").equals(entry.event().id())
                && data.getUuid("PlayerUuid").equals(subject.getUuid()), "snapshot header must match its file");
            var main = SnapshotView.mainInventory(data);
            require(ItemStack.areEqual(main[27], sword), entry.event() + " must keep exact hotbar NBT");
            require(main[11].isOf(Items.DIRT) && main[11].getCount() == 32, "main inventory slot 20 must decode");
            require(main[37].isOf(Items.DIAMOND_CHESTPLATE), "chestplate must decode in armour order");
            require(main[40].isOf(Items.SHIELD), "offhand must decode");
            var ender = SnapshotView.enderChest(data);
            require(ender[3].isOf(Items.GOLDEN_APPLE) && ender[3].getCount() == 5, "ender chest must decode");
        }
        NbtCompound travel = store.read(subject.getUuid(), entries.get(2).id());
        require(travel.getString("Dimension").equals("minecraft:the_nether")
            && travel.getString("Detail").equals("minecraft:overworld -> minecraft:the_nether"),
            "dimension snapshot must record origin and destination");
        require(store.read(subject.getUuid(), entries.get(0).id()).getString("Detail").contains("death.attack.wither"),
            "death snapshot must record the cause");

        server.getUserCache().add(subject.getGameProfile());
        var admin = player(server, overworld, "HistoryAdmin");
        server.getPlayerManager().addToOperators(admin.getGameProfile());
        require(admin.hasPermissionLevel(4), "read-only test requires a level-4 operator");
        var moderator = player(server, overworld, "HistoryGuest");
        var dispatcher = server.getCommandManager().getDispatcher();
        require(dispatcher.execute("invhistory list HistoryTest", admin.getCommandSource()) == 4,
            "op list must report every snapshot");
        boolean denied = false;
        try {
            dispatcher.execute("invhistory list HistoryTest", moderator.getCommandSource());
        } catch (CommandSyntaxException expected) {
            denied = true;
        }
        require(denied, "non-op players must not see inventory history");

        long joinId = entries.get(3).id();
        require(dispatcher.execute("invhistory view HistoryTest " + joinId, admin.getCommandSource()) == 1,
            "view must open the snapshot");
        require(admin.currentScreenHandler instanceof SnapshotScreen, "view must open the history GUI");
        var screen = (SnapshotScreen) admin.currentScreenHandler;
        ScreenHandler handler = screen;
        require(ItemStack.areItemsEqual(handler.getSlot(27).getStack(), sword)
            && handler.getSlot(27).getStack().getName().getString().equals("Exact history"), "GUI must show hotbar");
        admin.getInventory().setStack(0, new ItemStack(Items.STICK, 2));
        for (SlotActionType action : SlotActionType.values()) {
            int button = action == SlotActionType.CLONE ? 2 : action == SlotActionType.THROW ? 1 : 0;
            requireBlocked(handler, admin, 27, button, action);
        }
        requireBlocked(handler, admin, 11, 0, SlotActionType.QUICK_MOVE);
        handler.onSlotClick(SnapshotScreen.ENDER, 0, SlotActionType.PICKUP, admin);
        require(screen.pageTitle().equals("Ender chest") && handler.getSlot(3).getStack().isOf(Items.GOLDEN_APPLE),
            "ender chest page must open");
        requireBlocked(handler, admin, 3, 0, SlotActionType.QUICK_MOVE);
        handler.onSlotClick(SnapshotScreen.BACK, 0, SlotActionType.PICKUP, admin);
        require(screen.pageTitle().equals("Inventory"), "back must return to the inventory page");
        String backpackResult = "";
        if (bags != null) backpackResult = bags.verify(store.read(subject.getUuid(), joinId), admin, screen, handler);

        verifyPruning(store, subject.getUuid(), entries.get(3).file());
        return backpackResult;
    }

    private static void verifyPruning(SnapshotStore store, UUID player, Path sample) throws Exception {
        long now = System.currentTimeMillis();
        Path directory = sample.getParent();
        Path expired = directory.resolve((now - Duration.ofDays(8).toMillis()) + "-join.dat");
        Path retained = directory.resolve((now - Duration.ofDays(6).toMillis()) + "-leave.dat");
        Files.copy(sample, expired);
        Files.copy(sample, retained);
        int before = store.list(player).size();
        require(store.prune(now) == 1, "exactly the 8-day-old snapshot must be pruned");
        require(!Files.exists(expired) && Files.exists(retained) && store.list(player).size() == before - 1,
            "snapshots within 7 days must be kept");
        Files.delete(retained);
    }

    private static void requireBlocked(ScreenHandler handler, ServerPlayerEntity viewer, int slot,
                                       int button, SlotActionType action) {
        var inventory = viewer.getInventory().writeNbt(new NbtList());
        ItemStack before = handler.getSlot(slot).getStack().copy();
        ItemStack cursor = handler.getCursorStack().copy();
        handler.onSlotClick(slot, button, action, viewer);
        require(ItemStack.areEqual(before, handler.getSlot(slot).getStack())
            && ItemStack.areEqual(cursor, handler.getCursorStack())
            && inventory.equals(viewer.getInventory().writeNbt(new NbtList())),
            action + " must not transfer items from the read-only view");
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name) {
        var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name)) {
            @Override public void sendMessage(Text message) {}
        };
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Uses real Sophisticated Backpacks storage; only loaded when that mod is present. */
    private record Backpacks(UUID outer) {
        static UUID prepareShutdown() {
            UUID uuid = UUID.randomUUID();
            var storage = net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage.get();
            storage.setBackpackContents(uuid, shutdownContents());
            storage.markDirty();
            return uuid;
        }

        static void verifyShutdown(Path root, UUID uuid) throws Exception {
            var saved = NbtIo.readCompressed(root.resolve("data").resolve("sophisticatedbackpacks.dat").toFile());
            var entries = saved.getCompound("data").getList("backpackContents", 10);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.getCompound(i);
                if (entry.containsUuid("uuid") && entry.getUuid("uuid").equals(uuid)) {
                    require(entry.getCompound("contents").equals(shutdownContents()),
                        "saved backpack entry contents differ from the final-tick data");
                    return;
                }
            }
            throw new IllegalStateException("shutdown did not persist the fresh backpack contents");
        }

        private static NbtCompound shutdownContents() {
            return contents(new ItemStack(Items.GOLD_INGOT, 7), 2, 7);
        }

        static Backpacks give(ServerPlayerEntity player) {
            var storage = net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage.get();
            UUID outer = UUID.randomUUID();
            UUID inner = UUID.randomUUID();
            var innerBag = bag("iron_backpack", inner);
            storage.setBackpackContents(inner, contents(new ItemStack(Items.EMERALD, 3), 0, 3));
            NbtCompound outerContents = contents(new ItemStack(Items.DIAMOND), 5, 2048);
            NbtCompound nested = innerBag.writeNbt(new NbtCompound());
            nested.putInt("Slot", 6);
            outerContents.getCompound("inventory").getList("Items", 10).add(nested);
            storage.setBackpackContents(outer, outerContents);
            var outerBag = bag("netherite_backpack", outer);
            outerBag.setCustomName(Text.literal("Tiny's bag"));
            player.getInventory().setStack(1, outerBag);
            return new Backpacks(outer);
        }

        String verify(NbtCompound snapshot, ServerPlayerEntity admin, SnapshotScreen screen, ScreenHandler handler) {
            require(snapshot.getBoolean("BackpackContentsComplete")
                && snapshot.getList("BackpackContents", 10).size() == 2, "nested backpack contents must be archived");
            require(SnapshotView.backpackItems(SnapshotView.archivedBackpacks(snapshot).get(outer)).get(5).getCount() == 2048,
                "the archive must preserve the full stored count");
            var storage = net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage.get();
            NbtCompound before = storage.writeNbt(new NbtCompound()).copy();
            requireBlocked(handler, admin, 28, 0, SlotActionType.QUICK_MOVE);
            handler.onSlotClick(28, 0, SlotActionType.PICKUP, admin);
            require(screen.pageTitle().equals("Tiny's bag"), "clicking a backpack must open its archived contents");
            var diamonds = handler.getSlot(5).getStack();
            require(diamonds.isOf(Items.DIAMOND) && diamonds.getCount() == 64, "realCount must display capped");
            require(handler.getSlot(6).getStack().isOf(Registries.ITEM.get(
                new Identifier("sophisticatedbackpacks", "iron_backpack"))), "nested backpack must be shown");
            requireBlocked(handler, admin, 5, 0, SlotActionType.QUICK_MOVE);
            requireBlocked(handler, admin, 6, 0, SlotActionType.QUICK_MOVE);
            handler.onSlotClick(6, 0, SlotActionType.PICKUP, admin);
            var emeralds = handler.getSlot(0).getStack();
            require(emeralds.isOf(Items.EMERALD) && emeralds.getCount() == 3, "nested backpack contents must be shown");
            requireBlocked(handler, admin, 0, 0, SlotActionType.QUICK_MOVE);
            handler.onSlotClick(SnapshotScreen.BACK, 0, SlotActionType.PICKUP, admin);
            handler.onSlotClick(SnapshotScreen.BACK, 0, SlotActionType.PICKUP, admin);
            require(screen.pageTitle().equals("Inventory"), "back must return from a nested backpack");
            require(storage.writeNbt(new NbtCompound()).equals(before), "viewing must not mutate live backpack storage");
            return "; read-only nested Sophisticated Backpacks archive and realCount display";
        }

        private static ItemStack bag(String type, UUID uuid) {
            var bag = new ItemStack(Registries.ITEM.get(new Identifier("sophisticatedbackpacks", type)));
            require(!bag.isEmpty(), type + " must be registered");
            bag.getOrCreateNbt().putUuid("contentsUuid", uuid);
            return bag;
        }

        private static NbtCompound contents(ItemStack stack, int slot, int realCount) {
            NbtCompound contents = new NbtCompound();
            NbtCompound inventory = new NbtCompound();
            inventory.putInt("Size", 27);
            NbtList items = new NbtList();
            NbtCompound item = stack.writeNbt(new NbtCompound());
            item.putInt("Slot", slot);
            item.putInt("realCount", realCount);
            items.add(item);
            inventory.put("Items", items);
            contents.put("inventory", inventory);
            return contents;
        }
    }
}
