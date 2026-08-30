package com.gangs.rankbadges;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class RankBadgesMod implements ModInitializer {
    private static final Identifier BADGE_FONT = new Identifier("rankbadges", "badges");
    private static final String OWNER_TEAM = "rankbadges_owner";
    private static final String MOD_TEAM = "rankbadges_mod";
    private static final String PLAYER_TEAM = "rankbadges_player";

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::refreshAll);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> this.applyRank(server, handler.getPlayer()));
    }

    private void refreshAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            this.applyRank(server, player);
        }
    }

    private void applyRank(MinecraftServer server, ServerPlayerEntity player) {
        Scoreboard scoreboard = server.getScoreboard();
        Team team = this.getTeam(scoreboard, this.rankFor(player));
        scoreboard.addPlayerToTeam(player.getEntityName(), team);
    }

    private Team getTeam(Scoreboard scoreboard, Rank rank) {
        String teamName = switch (rank) {
            case OWNER -> OWNER_TEAM;
            case MOD -> MOD_TEAM;
            case PLAYER -> PLAYER_TEAM;
        };
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
        }

        team.setPrefix(rank.getPrefix());
        team.setColor(rank.color);
        team.setFriendlyFireAllowed(true);

        return team;
    }

    private Rank rankFor(ServerPlayerEntity player) {
        String primaryGroup = this.luckPermsPrimaryGroup(player.getGameProfile());
        if ("owner".equals(primaryGroup)) {
            return Rank.OWNER;
        }
        if ("mod".equals(primaryGroup) || "moderator".equals(primaryGroup) || player.hasPermissionLevel(2)) {
            return Rank.MOD;
        }
        return Rank.PLAYER;
    }

    private String luckPermsPrimaryGroup(GameProfile profile) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, profile.getId());
            if (user == null) {
                return "";
            }
            return ((String) user.getClass().getMethod("getPrimaryGroup").invoke(user)).toLowerCase(Locale.ROOT);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private enum Rank {
        OWNER(Text.literal("\ue001 ").setStyle(Style.EMPTY.withFont(BADGE_FONT)), Formatting.BLUE),
        MOD(Text.literal("[mod] "), Formatting.GREEN),
        PLAYER(Text.literal("[player] "), Formatting.WHITE);

        private final Text prefix;
        private final Formatting color;

        Rank(Text prefix, Formatting color) {
            this.prefix = prefix;
            this.color = color;
        }

        public Text getPrefix() {
            return this.prefix;
        }
    }
}
