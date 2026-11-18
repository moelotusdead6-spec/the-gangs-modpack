package com.gangs.invhistory;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;

final class Perms {
    static final String VIEW = "gangsinvhistory.view";
    static final int VIEW_LEVEL = 3;
    private static final boolean API = FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0");

    private Perms() {}

    static boolean check(ServerCommandSource source, String node, int level) {
        return API ? Api.check(source, node, level) : source.hasPermissionLevel(level);
    }

    // Isolated so the optional permissions API is only linked when it is installed.
    private static final class Api {
        static boolean check(ServerCommandSource source, String node, int level) {
            return Permissions.check(source, node, level);
        }
    }
}
