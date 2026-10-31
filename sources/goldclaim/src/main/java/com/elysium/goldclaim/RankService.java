package com.elysium.goldclaim;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RankService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Ranks");
    static final Set<String> GANG = Set.of("grimmnm", "brooklyn0_0", "vendettaaaaaaa", "ale432",
        "cletus_jp", "hauntdhollow", "starhauntu", "koolkat404", "wooberson", "kunoisback");
    private static LuckPerms permissions;
    private static boolean migrated;
    private static int ticks;

    enum Rank {
        OWNER("00_owner", "OWNER", Formatting.BLUE),
        MOD("01_mod", "MOD", Formatting.GREEN),
        GANG("02_gang", "Gang", Formatting.GOLD),
        DRIFTERS("03_drifters", "Drifters", Formatting.GRAY);

        final String team;
        final String label;
        final Formatting color;

        Rank(String team, String label, Formatting color) {
            this.team = team;
            this.label = label;
            this.color = color;
        }
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            migrated = false;
            try {
                permissions = LuckPermsProvider.get();
            } catch (IllegalStateException | NoClassDefFoundError unavailable) {
                LOGGER.error("LuckPerms unavailable: rank-group migration cannot run. Tab labels still use the configured roster.", unavailable);
                return;
            }
            LuckPerms api = permissions;
            CompletableFuture.runAsync(() -> migrate(api)).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.error("Rank migration failed. Player is deleted only after all users, groups and tracks save; restart to retry.", failure);
                } else {
                    server.execute(() -> {
                        migrated = true;
                        refresh(server);
                        removeLegacyTeams(server.getScoreboard());
                        LOGGER.info("Rank migration complete: Owner > Mod > Gang > Drifters; Player group removed.");
                    });
                }
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks >= 20) {
                ticks = 0;
                refresh(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            permissions = null;
            migrated = false;
            ticks = 0;
        });
    }

    static Rank rank(String name, boolean owner, boolean moderator) {
        if (owner || "oux_y".equalsIgnoreCase(name)) return Rank.OWNER;
        if (moderator || "rootamiss".equalsIgnoreCase(name)) return Rank.MOD;
        return GANG.contains(name.toLowerCase(Locale.ROOT)) ? Rank.GANG : Rank.DRIFTERS;
    }

    static boolean ready() {
        return migrated;
    }

    static void apply(ServerPlayerEntity player, MinecraftServer server) {
        User user = permissions == null ? null : permissions.getUserManager().getUser(player.getUuid());
        Set<String> groups = new java.util.HashSet<>();
        if (user != null) {
            groups.add(user.getPrimaryGroup());
            for (Group group : user.getInheritedGroups(user.getQueryOptions())) groups.add(group.getName());
            if (migrated && ensureMembership(user, player.getGameProfile().getName())) {
                permissions.getUserManager().saveUser(user).whenComplete((ignored, failure) -> {
                    if (failure != null) LOGGER.error("Could not persist ranks for {}", player.getUuid(), failure);
                });
            }
        }
        Rank rank = rank(player.getGameProfile().getName(), groups.contains("owner"),
            groups.contains("mod") || groups.contains("moderator") || player.hasPermissionLevel(2));
        Scoreboard scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(rank.team);
        if (team == null) team = scoreboard.addTeam(rank.team);
        Text prefix = Text.literal("[" + rank.label + "] ").formatted(rank.color);
        if (!team.getPrefix().equals(prefix)) team.setPrefix(prefix);
        if (team.getColor() != rank.color) team.setColor(rank.color);
        if (!team.isFriendlyFireAllowed()) team.setFriendlyFireAllowed(true);
        if (scoreboard.getPlayerTeam(player.getEntityName()) != team) {
            scoreboard.addPlayerToTeam(player.getEntityName(), team);
        }
    }

    private static void refresh(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) apply(player, server);
    }

    private static void removeLegacyTeams(Scoreboard scoreboard) {
        for (String name : List.of("owner", "rankbadges_owner", "mod", "player", "rankbadges_player")) {
            Team team = scoreboard.getTeam(name);
            if (team != null) scoreboard.removeTeam(team);
        }
    }

    static synchronized void migrate(LuckPerms api) {
        var groups = api.getGroupManager();
        groups.loadAllGroups().join();
        Group drifters = groups.createAndLoadGroup("drifters").join();
        groups.createAndLoadGroup("gang").join();
        Group old = groups.getGroup("player");
        if (old != null) {
            validateInheritance(groups.getLoadedGroups());
            for (Node node : old.data().toCollection()) {
                for (Node existing : drifters.data().toCollection()) {
                    if (node.getKey().equals(existing.getKey()) && node.getContexts().equals(existing.getContexts())
                            && !node.equals(existing, net.luckperms.api.node.NodeEqualityPredicate.EXACT)) {
                        throw new IllegalStateException("Player and Drifters have conflicting values/expiry for "
                            + node.getKey() + ". Resolve the conflict before migrating; Player is retained.");
                    }
                }
            }
            for (Node node : old.data().toCollection()) drifters.data().add(node);
            replaceParents(drifters);
            groups.saveGroup(drifters).join();
            for (Group group : groups.getLoadedGroups()) {
                if (!group.getName().equals("player") && replaceParents(group)) groups.saveGroup(group).join();
            }
        }
        Set<java.util.UUID> users = new java.util.HashSet<>(api.getUserManager().getUniqueUsers().join());
        for (User user : api.getUserManager().getLoadedUsers()) users.add(user.getUniqueId());
        for (var id : users) {
            User user = api.getUserManager().loadUser(id).join();
            try {
                boolean changed = replaceParents(user);
                if ("player".equals(user.getPrimaryGroup())) {
                    user.data().add(InheritanceNode.builder("drifters").build());
                    user.setPrimaryGroup("drifters");
                    changed = true;
                }
                changed |= ensureMembership(user, user.getUsername());
                if (changed) api.getUserManager().saveUser(user).join();
            } finally {
                api.getUserManager().cleanupUser(user);
            }
        }
        if (old != null) {
            api.getTrackManager().loadAllTracks().join();
            for (var track : api.getTrackManager().getLoadedTracks()) {
                if (!track.containsGroup("player")) continue;
                List<String> replacement = trackGroups(track.getGroups());
                track.clearGroups();
                for (String name : replacement) {
                    Group group = groups.getGroup(name);
                    if (group == null) throw new IllegalStateException("Track references missing group " + name);
                    track.appendGroup(group);
                }
                api.getTrackManager().saveTrack(track).join();
            }
            groups.deleteGroup(old).join();
        }
    }

    private static void validateInheritance(Set<Group> groups) {
        var graph = new java.util.HashMap<String, Set<String>>();
        for (Group group : groups) {
            String name = group.getName().equals("player") ? "drifters" : group.getName();
            Set<String> parents = graph.computeIfAbsent(name, ignored -> new java.util.HashSet<>());
            for (Node node : group.data().toCollection()) {
                if (node instanceof InheritanceNode parent && parent.getValue()) {
                    String target = parent.getGroupName().equals("player") ? "drifters" : parent.getGroupName();
                    if (name.equals("drifters") && parent.getGroupName().equals("player")) continue;
                    parents.add(target);
                }
            }
        }
        validateInheritance(graph, "drifters", new java.util.HashSet<>());
    }

    static void validateInheritance(java.util.Map<String, Set<String>> graph, String group, Set<String> path) {
        if (!path.add(group)) throw new IllegalStateException("Player-to-Drifters migration would create an inheritance cycle at " + group);
        for (String parent : graph.getOrDefault(group, Set.of())) validateInheritance(graph, parent, path);
        path.remove(group);
    }

    static List<String> trackGroups(List<String> names) {
        Set<String> result = new LinkedHashSet<>();
        for (String name : names) result.add(name.equals("player") ? "drifters" : name);
        return new ArrayList<>(result);
    }

    private static boolean replaceParents(PermissionHolder holder) {
        boolean changed = false;
        for (Node node : new ArrayList<>(holder.data().toCollection())) {
            if (node instanceof InheritanceNode parent && parent.getGroupName().equals("player")) {
                // toBuilder retains contexts, value and expiry, including negative/temporary membership.
                if (!(holder instanceof Group group && group.getName().equals("drifters"))) {
                    holder.data().add(parent.toBuilder().group("drifters").build());
                }
                holder.data().remove(node);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean ensureMembership(User user, String name) {
        boolean changed = false;
        if ("default".equals(user.getPrimaryGroup()) || "player".equals(user.getPrimaryGroup())) {
            changed |= user.data().add(InheritanceNode.builder("drifters").build()).wasSuccessful();
            changed |= user.setPrimaryGroup("drifters").wasSuccessful();
        }
        if (name != null && GANG.contains(name.toLowerCase(Locale.ROOT))) {
            changed |= user.data().add(InheritanceNode.builder("gang").build()).wasSuccessful();
        }
        return changed;
    }
}
