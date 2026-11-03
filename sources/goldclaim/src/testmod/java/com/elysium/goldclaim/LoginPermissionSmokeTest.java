package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.util.Tristate;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

final class LoginPermissionSmokeTest {
    private static boolean tested;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (tested || server.getTicks() < 30) return;
            tested = true;
            try {
                var api = LuckPermsProvider.get();
                var uuid = UUID.randomUUID();
                var user = api.getUserManager().loadUser(uuid).join();
                var group = api.getGroupManager().createAndLoadGroup("sales_fixture").join();
                group.data().clear();
                user.data().clear();
                user.data().add(InheritanceNode.builder("sales_fixture").build());
                user.getCachedData().invalidate();
                var player = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(uuid, "LoginFixture"));
                player.networkHandler = new ServerPlayNetworkHandler(server,
                    new ClientConnection(NetworkSide.SERVERBOUND), player);
                var dispatcher = server.getCommandManager().getDispatcher();
                var source = player.getCommandSource();
                for (String name : List.of("gs", "gs history", "gs mine", "gs add", "ec", "enderchest")) {
                    var words = name.split(" ");
                    var command = dispatcher.getRoot().getChild(words[0]);
                    if (words.length > 1) command = command.getChild(words[1]);
                    require(command != null, "Missing command " + name);
                    String permission = "gangsales.command." + (name.equals("enderchest") ? "ec" : name.replace(' ', '.'));
                    require(user.getCachedData().getPermissionData(user.getQueryOptions()).checkPermission(permission) == Tristate.UNDEFINED,
                        "Fixture must have no implicit permission grant: " + permission);
                    require(command.canUse(source), "Default public command inaccessible: " + name);
                    var deny = Node.builder(permission).value(false).build();
                    user.data().add(deny);
                    user.getCachedData().invalidate();
                    require(!command.canUse(source), "Denied command accessible: " + name);
                    server.getPlayerManager().sendCommandTree(player);
                    user.data().remove(deny);
                    var allow = Node.builder(permission).value(true).build();
                    user.data().add(allow);
                    user.getCachedData().invalidate();
                    require(command.canUse(source), "Allowed command inaccessible: " + name);
                    server.getPlayerManager().sendCommandTree(player);
                    user.data().remove(allow);
                    user.getCachedData().invalidate();
                }
                player.getEnderChestInventory().setStack(0, new ItemStack(Items.EMERALD, 3));
                for (String name : List.of("ec", "enderchest")) {
                    require(dispatcher.execute(name, source) == 1, "Ender chest command failed: " + name);
                    require(player.currentScreenHandler.getSlot(0).getStack().getCount() == 3
                            && player.currentScreenHandler.getSlot(0).getStack().isOf(Items.EMERALD),
                        "Both aliases must open the player's actual ender chest");
                    player.closeHandledScreen();
                }
                player.getInventory().setStack(player.getInventory().selectedSlot, new ItemStack(Items.DIAMOND, 2));
                require(dispatcher.execute("gs add 1 7", source) == 1, "Default player must be able to list items");
                for (String name : List.of("gs", "gs mine", "gs history")) {
                    require(dispatcher.execute(name, source) == 1, "Sales command failed: " + name);
                    player.closeHandledScreen();
                }
                server.getPlayerManager().getPlayerList().add(player);
                server.getOverworld().onPlayerConnected(player);
                player.refreshPositionAndAngles(32.5, 100, 32.5, 0, 0);
                server.getOverworld().getChunkManager().updatePosition(player);
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: real LuckPerms, undefined/denied/allowed gs and all subcommands/ec/enderchest; "
                    + "both aliases open the actual ender chest; public listing/browse/mine/history execute; "
                    + "complete login command tree serialization and player chunk tracking.\n");
                server.stop(false);
            } catch (Exception failure) {
                throw new IllegalStateException("Login permission smoke failed", failure);
            }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
