package com.gangs.gangsgems;

import java.util.Collection;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StaffAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger("GangGems/StaffAccess");
    private static final Set<String> STAFF = Set.of("mod", "moderator", "admin", "owner");
    private static final Set<String> ADMINS = Set.of("admin", "owner");
    private static boolean reportedUnavailable;

    private StaffAccess() {}

    static boolean canMonitor(ServerCommandSource source) {
        return hasGroups(source, false);
    }

    static boolean canRemove(ServerCommandSource source) {
        return hasGroups(source, true);
    }

    static boolean matches(Collection<String> groups, boolean adminOnly) {
        Set<String> permitted = adminOnly ? ADMINS : STAFF;
        return groups.stream().anyMatch(permitted::contains);
    }

    private static boolean hasGroups(ServerCommandSource source, boolean adminOnly) {
        if (source.getEntity() == null) return source.hasPermissionLevel(4);
        var player = source.getPlayer();
        if (player == null) return false;
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            reportUnavailable("LuckPerms is absent; player staff access is denied.");
            return false;
        }
        return LuckPermsAccess.hasGroups(player.getUuid(), adminOnly);
    }

    private static final class LuckPermsAccess {
        private static boolean hasGroups(java.util.UUID player, boolean adminOnly) {
            try {
                var user = net.luckperms.api.LuckPermsProvider.get().getUserManager().getUser(player);
                if (user == null) {
                    LOGGER.warn("No loaded LuckPerms user for {}; staff access denied.", player);
                    return false;
                }
                var groups = new java.util.HashSet<String>();
                groups.add(user.getPrimaryGroup());
                user.getInheritedGroups(user.getQueryOptions()).forEach(group -> groups.add(group.getName()));
                return matches(groups, adminOnly);
            } catch (IllegalStateException unavailable) {
                reportUnavailable("LuckPerms is not ready; player staff access is denied.");
                return false;
            }
        }
    }

    private static void reportUnavailable(String message) {
        if (!reportedUnavailable) {
            reportedUnavailable = true;
            LOGGER.error(message);
        }
    }
}
