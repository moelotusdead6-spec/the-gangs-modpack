package com.gangs.ouxbadges.smoke;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks that GoldClaim's ranks reach Oux Badges' teams, prefixes and tab order. */
public final class OuxBadgesSmokeTest implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("OuxBadgesSmoke");
    private final List<String> lines = new ArrayList<>();
    private boolean failed;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
    }

    private void start(MinecraftServer server) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            List<CompletableFuture<User>> loads = new ArrayList<>();
            for (String name : List.of("Oux_y", "Rootamiss", "grimmnm", "NewDrifter")) {
                loads.add(luckPerms.getUserManager().loadUser(id(name), name));
            }
            CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) ->
                server.execute(() -> {
                    if (failure != null) {
                        fail("LuckPerms user load failed: " + failure);
                        finish(server);
                    } else {
                        run(server, luckPerms);
                    }
                }));
        } catch (Throwable failure) {
            LOGGER.error("Smoke test crashed", failure);
            fail("ERROR " + failure);
            finish(server);
        }
    }

    private void run(MinecraftServer server, LuckPerms luckPerms) {
        try {
            Class<?> ranks = Class.forName("com.elysium.goldclaim.RankService");
            Method apply = ranks.getDeclaredMethod("apply", ServerPlayer.class, MinecraftServer.class);
            Method refresh = ranks.getDeclaredMethod("refresh", MinecraftServer.class);
            apply.setAccessible(true);
            refresh.setAccessible(true);
            Method ouxRefresh = Class.forName("oux_badges_hiec7dbm.BadgeRuntime")
                .getMethod("refreshPlayer", ServerPlayer.class, Logger.class);

            check(server, luckPerms, apply, "Oux_y", "1_owner", "Owner", 0xF6C344);
            check(server, luckPerms, apply, "Rootamiss", "2_mod", "Mod", 0x18A86B);
            check(server, luckPerms, apply, "grimmnm", "3_gang", "Gang", 0x8A43B8);
            check(server, luckPerms, apply, "NewDrifter", "4_drifter", "Drifter", 0xE7C546);

            // Oux Badges' default gang badge matched the LuckPerms gang group; it must stay disabled.
            User drifter = luckPerms.getUserManager().getUser(id("NewDrifter"));
            drifter.data().add(InheritanceNode.builder("gang").build());
            check(server, luckPerms, apply, "NewDrifter", "4_drifter", "Drifter", 0xE7C546);
            drifter.data().remove(InheritanceNode.builder("gang").build());

            GameProfile profile = new GameProfile(id("NewDrifter"), "NewDrifter");
            server.getPlayerList().op(profile);
            check(server, luckPerms, apply, "NewDrifter", "2_mod", "Mod", 0x18A86B);
            server.getPlayerList().deop(profile);
            check(server, luckPerms, apply, "NewDrifter", "4_drifter", "Drifter", 0xE7C546);

            ServerPlayer owner = player(server, "Oux_y");
            ouxRefresh.invoke(null, owner, LOGGER);
            expect("oux_badges_1_owner".equals(teamName(server, "Oux_y")), "Oux Badges' own refresh keeps Owner");

            Scoreboard scoreboard = server.getScoreboard();
            scoreboard.addPlayerTeam("00_owner");
            scoreboard.addPlayerToTeam("Legacy", scoreboard.getPlayerTeam("00_owner"));
            refresh.invoke(null, server);
            expect(scoreboard.getPlayerTeam("00_owner") == null, "legacy 00_owner team removed");

            List<String> order = new ArrayList<>();
            for (String name : List.of("NewDrifter", "grimmnm", "Oux_y", "Rootamiss")) order.add(teamName(server, name));
            List<String> sorted = new ArrayList<>(order);
            sorted.sort(String::compareTo);
            lines.add("vanilla tab order by team: " + sorted);
            expect(sorted.equals(List.of("oux_badges_1_owner", "oux_badges_2_mod", "oux_badges_3_gang", "oux_badges_4_drifter")),
                "team names sort Owner > Mod > Gang > Drifter");
        } catch (Throwable failure) {
            LOGGER.error("Smoke test crashed", failure);
            fail("ERROR " + failure);
        }
        finish(server);
    }

    private void check(MinecraftServer server, LuckPerms luckPerms, Method apply, String name, String key, String label, int color)
            throws Exception {
        ServerPlayer player = player(server, name);
        apply.invoke(null, player, server);
        String team = teamName(server, name);
        PlayerTeam playerTeam = server.getScoreboard().getPlayersTeam(name);
        Component prefix = playerTeam == null ? Component.empty() : playerTeam.getPlayerPrefix();
        Style style = prefix.getStyle();
        User user = luckPerms.getUserManager().getUser(id(name));
        List<String> transientBadges = new ArrayList<>();
        for (Node node : user.transientData().toCollection()) {
            if (node.getKey().startsWith("oux.badges.")) transientBadges.add(node.getKey());
        }
        boolean saved = user.data().toCollection().stream().anyMatch(node -> node.getKey().startsWith("oux.badges."));
        lines.add(name + ": team=" + team + " prefix='" + prefix.getString() + "' color="
            + (style.getColor() == null ? "none" : style.getColor().serialize()) + " bold=" + style.isBold()
            + " transient=" + transientBadges + " saved=" + saved);
        expect(("oux_badges_" + key).equals(team), name + " is in oux_badges_" + key);
        expect(("[" + label + "] ").equals(prefix.getString()), name + " prefix is [" + label + "]");
        expect(style.isBold(), name + " prefix is bold");
        // The owner badge alternates between two colors.
        expect(style.getColor() != null && (style.getColor().getValue() == color || key.equals("1_owner")),
            name + " prefix color");
        expect(transientBadges.equals(List.of("oux.badges." + key)), name + " has exactly one transient badge");
        expect(!saved, name + " badge permission is not saved");
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        return FakePlayer.get(server.overworld(), new GameProfile(id(name), name));
    }

    private static String teamName(MinecraftServer server, String name) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(name);
        return team == null ? "none" : team.getName();
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private void expect(boolean condition, String message) {
        if (!condition) fail("FAILED: " + message);
    }

    private void fail(String message) {
        failed = true;
        lines.add(message);
    }

    private void finish(MinecraftServer server) {
        List<String> output = new ArrayList<>();
        output.add(failed ? "FAIL" : "PASS");
        output.addAll(lines);
        try {
            Files.write(Path.of("ouxbadges-smoke-result.txt"), output);
        } catch (Exception failure) {
            LOGGER.error("Could not write result", failure);
        }
        for (String line : output) LOGGER.info(line);
        server.halt(false);
    }
}
