/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.minecraft.block.Blocks
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.client.render.VertexFormatElement0
 *  net.minecraft.server.world.ServerWorld
 *  net.minecraft.registry.RegistryKey
 *  net.minecraft.loot.entry.LootPoolEntry24
 *  net.minecraft.server.MinecraftServer
 */
package com.elysium.goldclaim.portal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;

final class MultiworldPortalBridge {
    private static final String PORTAL_CLASS = "me.isaiah.multiworld.portal.Portal";
    private static final String PORTAL_COMMAND_CLASS = "me.isaiah.multiworld.command.PortalCommand";

    private MultiworldPortalBridge() {
    }

    static void savePortal(String portalName, String ownerName, String worldId, String destination, BlockPos minPos, BlockPos maxPos) throws ReflectiveOperationException {
        Class<?> portalClass = Class.forName(PORTAL_CLASS);
        Constructor<?> constructor = portalClass.getConstructor(String.class, String.class, Identifier.class, String.class, String.class);
        Object portal = constructor.newInstance(portalName, ownerName, Identifier.tryParse((String)worldId), destination, MultiworldPortalBridge.formatLocation(minPos, maxPos));
        Method save = portalClass.getMethod("save", new Class[0]);
        save.invoke(portal, new Object[0]);
        Method refresh = portalClass.getMethod("refreshPortalArea", new Class[0]);
        refresh.invoke(portal, new Object[0]);
    }

    static void clearPortalBlocks(MinecraftServer server, BlockPos minPos, BlockPos maxPos, String worldId) {
        Identifier worldIdIdentifier = Identifier.tryParse((String)worldId);
        if (worldIdIdentifier == null) {
            return;
        }
        RegistryKey worldKey = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)worldIdIdentifier);
        ServerWorld world = server.getWorld(worldKey);
        if (world == null) {
            return;
        }
        int minX = Math.min(minPos.getX(), maxPos.getX());
        int minY = Math.min(minPos.getY(), maxPos.getY());
        int minZ = Math.min(minPos.getZ(), maxPos.getZ());
        int maxX = Math.max(minPos.getX(), maxPos.getX());
        int maxY = Math.max(minPos.getY(), maxPos.getY());
        int maxZ = Math.max(minPos.getZ(), maxPos.getZ());
        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    static void removeFromKnownPortals(String portalName) {
        try {
            Class<?> portalCommandClass = Class.forName(PORTAL_COMMAND_CLASS);
            Field knownPortalsField = portalCommandClass.getField("KNOWN_PORTALS");
            Object knownPortals = knownPortalsField.get(null);
            if (knownPortals instanceof Map) {
                Map map = (Map)knownPortals;
                map.remove(portalName.toLowerCase(Locale.ROOT));
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    static void reload(MinecraftServer server) {
        try {
            Class<?> portalClass = Class.forName(PORTAL_CLASS);
            Method reinit = portalClass.getMethod("reinit_portals_from_config", MinecraftServer.class);
            reinit.invoke(null, server);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    static String formatLocation(BlockPos minPos, BlockPos maxPos) {
        return minPos.getX() + "," + minPos.getY() + "," + minPos.getZ() + ":" + maxPos.getX() + "," + maxPos.getY() + "," + maxPos.getZ();
    }
}
