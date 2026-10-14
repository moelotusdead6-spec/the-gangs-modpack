package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class LegacyDimensionSmokeTest {
    static void verify(MinecraftServer server) {
        ServerWorld wild = server.getOverworld();
        ServerPlayerEntity player = player(server, wild, UUID.fromString("00000000-0000-0000-0000-000000000117"));
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
        player.experienceLevel = 23;
        for (String home : java.util.List.of("stale-nether", "stale-end")) {
            require(server.getCommandManager().executeWithPrefix(player.getCommandSource(), "home " + home) == 0,
                "stale /home falsely reported success");
            require(player.getServerWorld() == wild, "stale /home entered a closed dimension");
        }
        require(server.getCommandManager().executeWithPrefix(player.getCommandSource(), "home allowed-wild") == 1
                && player.getX() == 64.5 && player.getZ() == 64.5,
            "allowed /home was blocked or teleported to the wrong location");
        for (RegistryKey<World> id : java.util.List.of(World.NETHER, World.END)) {
            ServerWorld destination = server.getWorld(id);
            require(destination != null, "legacy world must exist without deletion");
            player.teleport(destination, 0.5, 100, 0.5, 0, 0);
            require(player.getServerWorld() == wild, "direct teleport into closed dimension succeeded");
            require(!player.teleport(destination, 0.5, 100, 0.5, Set.of(), 0, 0),
                "command teleport into closed dimension reported success");
            player.moveToWorld(destination);
            require(player.getServerWorld() == wild, "portal move into closed dimension succeeded");
            player.setSpawnPoint(id, new BlockPos(0, 100, 0), 0, true, false);
            require(player.getSpawnPointPosition() == null, "closed respawn point was accepted");
            require(server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),
                "execute in " + id.getValue() + " run tp @s 0 100 0") == 0,
                "operator /tp falsely reported success");
            require(player.getServerWorld() == wild, "operator /tp bypassed closed-dimension guard");
        }
        for (String id : GoldClaimMod.RSW_DIMENSIONS) {
            ServerWorld destination = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(id)));
            require(destination != null, "RSW destination is missing");
            player.teleport(destination, 20.5, 100, 20.5, 0, 0);
            require(player.getServerWorld() == destination, "allowed RSW teleport was blocked");
            player.teleport(wild, 20.5, 100, 20.5, 0, 0);
            require(player.getServerWorld() == wild, "allowed Wild return was blocked");
        }
        ServerWorld rsw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(GoldClaimMod.RSW_DIMENSION)));
        player.teleport(rsw, 32.5, 100, 32.5, 0, 0);
        player.moveToWorld(server.getWorld(World.NETHER));
        require(player.getServerWorld().getRegistryKey().getValue().toString().equals(GoldClaimMod.RSW_NETHER_DIMENSION),
            "RSW Nether portal routing was blocked or sent to the original Nether");
        player.moveToWorld(wild);
        require(player.getServerWorld() == rsw, "RSW Nether return did not route to RSW Overworld");
        player.moveToWorld(server.getWorld(World.END));
        require(player.getServerWorld().getRegistryKey().getValue().toString().equals(GoldClaimMod.RSW_THE_END_DIMENSION),
            "RSW End portal routing was blocked or sent to the original End: "
                + player.getServerWorld().getRegistryKey().getValue());
        player.moveToWorld(wild);
        require(player.getServerWorld() == rsw, "RSW End return did not route to RSW Overworld");
        require(player.getInventory().getStack(0).getCount() == 7 && player.experienceLevel == 23,
            "blocked/allowed teleports changed inventory or experience");
        for (RegistryKey<World> id : java.util.List.of(World.NETHER, World.END)) {
            ServerPlayerEntity stranded = player(server, server.getWorld(id));
            stranded.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 11));
            stranded.experienceLevel = 31;
            require(server.getCommandManager().executeWithPrefix(stranded.getCommandSource(), "sethome closed") == 0,
                "new home in a closed dimension was accepted");
            GoldClaimMod.getInstance().evacuateLegacyPlayer(stranded);
            require(stranded.getServerWorld().getRegistryKey().getValue().toString().equals("gangs:hub"),
                "saved-in-legacy-world recovery failed");
            require(stranded.getInventory().getStack(0).getCount() == 11 && stranded.experienceLevel == 31,
                "recovery changed inventory or experience");
            stranded.getServerWorld().removePlayer(stranded, Entity.RemovalReason.DISCARDED);
        }
        player.getServerWorld().removePlayer(player, Entity.RemovalReason.DISCARDED);
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world) {
        return player(server, world, UUID.randomUUID());
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, UUID id) {
        var player = new ServerPlayerEntity(server, world, new GameProfile(id, "LegacySmoke"));
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
