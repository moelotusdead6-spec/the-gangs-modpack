package com.elysium.goldclaim;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StaffLocatePolicy {
    private static final Set<String> STAFF = Set.of("mod", "moderator", "admin", "owner");
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Locate");
    private static boolean reportedUnavailable;

    private StaffLocatePolicy() {}

    static boolean allowed(boolean operator, Collection<String> groups) {
        return operator || groups.stream().anyMatch(STAFF::contains);
    }

    public static boolean canUse(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) return true;
        if (source.getPlayer() == null) return false;
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            reportUnavailable("LuckPerms is absent; non-operator /locate access is denied.");
            return false;
        }
        net.luckperms.api.LuckPerms permissions;
        try {
            permissions = LuckPermsProvider.get();
        } catch (IllegalStateException unavailable) {
            reportUnavailable("LuckPerms is not ready; non-operator /locate access is denied.");
            return false;
        }
        var user = permissions.getUserManager().getUser(source.getPlayer().getUuid());
        if (user == null) {
            LOGGER.warn("No loaded LuckPerms user for {}; /locate access denied.", source.getPlayer().getUuid());
            return false;
        }
        Set<String> groups = new HashSet<>();
        groups.add(user.getPrimaryGroup());
        for (Group group : user.getInheritedGroups(user.getQueryOptions())) groups.add(group.getName());
        return allowed(false, groups);
    }

    private static void reportUnavailable(String message) {
        if (!reportedUnavailable) {
            reportedUnavailable = true;
            LOGGER.error(message);
        }
    }
}
