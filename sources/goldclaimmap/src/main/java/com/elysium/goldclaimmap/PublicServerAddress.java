package com.elysium.goldclaimmap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublicServerAddress {
    static final String ADDRESS = "tgma.dedimc.io";
    private static final String LEGACY_ADDRESS = "gangs-adventure.playit.plus";
    private static final Logger LOGGER = LoggerFactory.getLogger("goldclaimmap");

    static void update(Path gameDirectory) {
        Path saved = gameDirectory.resolve("servers.dat");
        try {
            NbtCompound root = Files.exists(saved) ? NbtIo.read(saved.toFile()) : new NbtCompound();
            if (root == null) {
                throw new IOException("Could not read saved server list");
            }
            if (!migrate(root)) {
                return;
            }
            Path temporary = Files.createTempFile(gameDirectory, "tgma-servers-", ".dat");
            try {
                NbtIo.write(root, temporary.toFile());
                if (Files.exists(saved)) {
                    Path backup = Files.createTempFile(gameDirectory, "servers.dat.before-tgma-", ".bak");
                    Files.copy(saved, backup, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Backed up saved server list to {}", backup);
                }
                try {
                    Files.move(temporary, saved, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, saved, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            LOGGER.info("Updated public multiplayer address to {}", ADDRESS);
        } catch (IOException e) {
            LOGGER.error("Could not update the saved server address; original list is backed up or unchanged", e);
            throw new UncheckedIOException("Unable to update The Gangs server address", e);
        }
    }

    static boolean migrate(NbtCompound root) throws IOException {
        if (root.contains("servers") && !root.contains("servers", NbtElement.LIST_TYPE)) {
            throw new IOException("Saved servers field is not a list");
        }
        NbtList servers = root.getList("servers", NbtElement.COMPOUND_TYPE);
        if (root.contains("servers") && servers.size() == 0
                && !((NbtList) root.get("servers")).isEmpty()) {
            throw new IOException("Saved server list contains unexpected entry types");
        }
        boolean present = false;
        boolean changed = false;
        for (int i = 0; i < servers.size(); i++) {
            NbtCompound server = servers.getCompound(i);
            String address = server.getString("ip").trim();
            if (address.equalsIgnoreCase(LEGACY_ADDRESS)
                    || address.equalsIgnoreCase(LEGACY_ADDRESS + ":25565")) {
                server.putString("ip", ADDRESS);
                present = true;
                changed = true;
            } else if (address.equalsIgnoreCase(ADDRESS)
                    || address.equalsIgnoreCase(ADDRESS + ":25565")) {
                present = true;
            }
        }
        if (!present) {
            NbtCompound server = new NbtCompound();
            server.putString("name", "The Gangs Modded Adventure");
            server.putString("ip", ADDRESS);
            servers.add(server);
            changed = true;
        }
        if (changed) {
            root.put("servers", servers);
        }
        return changed;
    }
}
