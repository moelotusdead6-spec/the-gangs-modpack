package com.gangs.gravescompat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeathBackup {
    private static final Logger LOGGER = LoggerFactory.getLogger("gangsgravescompat");

    private DeathBackup() {}

    public static void save(ServerPlayerEntity player, DamageSource source) {
        Path directory = player.server.getSavePath(WorldSavePath.ROOT)
            .resolve("gangs-death-backups").resolve(player.getUuidAsString());
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            NbtCompound data = new NbtCompound();
            data.putInt("BackupVersion", 2);
            data.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
            data.put("Player", player.writeNbt(new NbtCompound()));
            data.putString("PlayerName", player.getGameProfile().getName());
            data.putString("Dimension", player.getWorld().getRegistryKey().getValue().toString());
            data.putString("DeathCause", Text.Serializer.toJson(source.getDeathMessage(player)));
            data.putLong("SavedAt", System.currentTimeMillis());
            if (FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks")
                && !BackpackBackup.addTo(data)) {
                LOGGER.error("Death backup for {} has missing external backpack contents: {}.",
                    player.getUuid(), data.get("MissingBackpackContents"));
                player.sendMessage(Text.literal(
                    "Some backpack data is missing from your death backup; notify an administrator."));
            }
            temporary = Files.createTempFile(directory, "death-", ".tmp");
            NbtIo.writeCompressed(data, temporary.toFile());
            Path destination = directory.resolve(data.getLong("SavedAt") + "-" + UUID.randomUUID() + ".dat");
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Saved pre-drop death inventory for {} at {}.",
                player.getGameProfile().getName(), destination);
        } catch (IOException failure) {
            LOGGER.error("Cannot save death inventory for {}.", player.getUuid(), failure);
            player.sendMessage(Text.literal("Death inventory backup failed; notify an administrator immediately."));
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    LOGGER.error("Cannot remove incomplete death backup {}.", temporary, failure);
                }
            }
        }
    }
}
