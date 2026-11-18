package com.gangs.invhistory;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InvHistoryMod implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("gangsinvhistory");
    static final boolean BACKPACKS = FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks");
    private static volatile SnapshotStore store;

    @Override
    public void onInitializeServer() {
        HistoryConfig config = HistoryConfig.load(FabricLoader.getInstance().getConfigDir().resolve("gangsinvhistory.json"));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            var created = new SnapshotStore(
                server.getSavePath(WorldSavePath.ROOT).resolve("gangs-inventory-history").normalize(), config.retention());
            created.start();
            store = created;
            LOGGER.info("Inventory history enabled at {} with {} day retention.", created.root(), config.retentionDays());
        });
        // Players are disconnected during shutdown before this runs, so their leave snapshots are flushed here.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            var current = store;
            store = null;
            if (current != null) current.close();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            capture(handler.player, SnapshotEvent.JOIN, ""));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            capture(handler.player, SnapshotEvent.LEAVE, ""));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            capture(player, SnapshotEvent.DIMENSION,
                origin.getRegistryKey().getValue() + " -> " + destination.getRegistryKey().getValue()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
            InvHistoryCommand.register(dispatcher));
    }

    public static SnapshotStore store() {
        return store;
    }

    public static void capture(ServerPlayerEntity player, SnapshotEvent event, String detail) {
        var current = store;
        if (current == null) return;
        // A snapshot failure must never interrupt joining, leaving, dying or travelling.
        try {
            current.submit(player.getUuid(), event, create(player, event, detail));
        } catch (RuntimeException failure) {
            LOGGER.error("Cannot capture {} inventory snapshot for {}.", event.id(), player.getUuid(), failure);
        }
    }

    static NbtCompound create(ServerPlayerEntity player, SnapshotEvent event, String detail) {
        NbtCompound data = new NbtCompound();
        data.putInt("FormatVersion", 1);
        data.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
        data.putString("Event", event.id());
        data.putString("Detail", detail);
        data.putUuid("PlayerUuid", player.getUuid());
        data.putString("PlayerName", player.getGameProfile().getName());
        data.putString("Dimension", player.getWorld().getRegistryKey().getValue().toString());
        data.putDouble("X", player.getX());
        data.putDouble("Y", player.getY());
        data.putDouble("Z", player.getZ());
        data.putLong("SavedAt", System.currentTimeMillis());
        data.put("Player", player.writeNbt(new NbtCompound()));
        if (BACKPACKS && !BackpackArchive.addTo(data)) {
            LOGGER.warn("{} snapshot for {} references backpacks without stored contents: {}.",
                event.id(), player.getGameProfile().getName(), data.get("MissingBackpackContents"));
        }
        // Detach from any live NBT that mods may hand out before writing on the I/O thread.
        return data.copy();
    }
}
