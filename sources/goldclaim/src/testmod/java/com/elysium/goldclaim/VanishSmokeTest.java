package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

final class VanishSmokeTest {
    private static boolean tested;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (tested || !RankService.ready()) return;
            tested = true;
            try {
                var api = Class.forName("me.drex.vanish.api.VanishAPI");
                var isVanished = api.getMethod("isVanished", Entity.class);
                var canSee = api.getMethod("canSeePlayer", ServerPlayerEntity.class, ServerPlayerEntity.class);
                var staff = player(server, "VanishStaff");
                var observer = player(server, "VanishObserver");
                var permissions = LuckPermsProvider.get();
                var staffUser = permissions.getUserManager().loadUser(staff.getUuid()).join();
                var observerUser = permissions.getUserManager().loadUser(observer.getUuid()).join();
                bindPermissions(staff, staffUser);
                bindPermissions(observer, observerUser);
                var command = server.getCommandManager().getDispatcher().getRoot().getChild("vanish");
                require(command != null, "/vanish must register");
                require(!command.canUse(observer.getCommandSource()), "Ordinary player must not get /vanish");
                staffUser.data().add(Node.builder("vanish.command.vanish").build());
                staffUser.getCachedData().invalidate();
                require(command.canUse(staff.getCommandSource()), "Explicit permission must grant /vanish");
                require(!(Boolean)isVanished.invoke(null, staff), "Player must start visible");
                var dispatcher = server.getCommandManager().getDispatcher();
                dispatcher.execute("vanish", staff.getCommandSource());
                require((Boolean)isVanished.invoke(null, staff), "/vanish must enable vanish");
                require(!(Boolean)canSee.invoke(null, staff, observer), "Ordinary observer must not see vanished staff");
                observerUser.data().add(Node.builder("vanish.feature.view").build());
                observerUser.getCachedData().invalidate();
                require((Boolean)canSee.invoke(null, staff, observer), "View permission must reveal vanished staff");
                server.getPlayerManager().sendCommandTree(staff);
                server.getPlayerManager().sendCommandTree(observer);
                dispatcher.execute("vanish", staff.getCommandSource());
                require(!(Boolean)isVanished.invoke(null, staff), "Second /vanish must restore visibility");
                require((Boolean)canSee.invoke(null, staff, observer), "Unvanished staff must be visible");
                require(Files.isRegularFile(Path.of("config", "vanish.hocon")), "Default config must be generated");
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: Vanish loads with GoldClaim, GangSales and LuckPerms; ordinary player denied; "
                    + "explicit permission grants /vanish; actual command toggles state; ordinary observer "
                    + "cannot see vanished staff; view permission restores visibility; command trees serialize; "
                    + "second toggle restores visibility; default config generated.\n");
                server.getPlayerManager().getPlayerList().remove(staff);
                server.getPlayerManager().getPlayerList().remove(observer);
                server.stop(false);
            } catch (Exception failure) {
                throw new IllegalStateException("Vanish compatibility smoke failed", failure);
            }
        });
    }

    private static void bindPermissions(ServerPlayerEntity player, net.luckperms.api.model.user.User user)
            throws ReflectiveOperationException {
        // Synthetic players bypass LuckPerms' normal login-time session binding.
        var internalUser = Class.forName("me.lucko.luckperms.common.api.implementation.ApiUser")
            .getMethod("cast", net.luckperms.api.model.user.User.class).invoke(null, user);
        Class.forName("me.lucko.luckperms.fabric.model.MixinUser")
            .getMethod("initializePermissions", Class.forName("me.lucko.luckperms.common.model.User"))
            .invoke(player, internalUser);
    }

    @SuppressWarnings("unchecked")
    private static ServerPlayerEntity player(MinecraftServer server, String name) throws ReflectiveOperationException {
        var player = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(UUID.randomUUID(), name));
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        server.getPlayerManager().getPlayerList().add(player);
        var field = PlayerManager.class.getDeclaredField("playerMap");
        field.setAccessible(true);
        var players = (Map<UUID, ServerPlayerEntity>)field.get(server.getPlayerManager());
        players.put(player.getUuid(), player);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
