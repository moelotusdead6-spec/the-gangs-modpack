package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

final class RswDragonSmokeTest {
    private static int ticks;
    private static ServerPlayerEntity player;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks > 1600) {
                throw new IllegalStateException("RSW End failed to spawn a True Ending dragon within 1600 ticks.");
            }
            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD,
                new Identifier(GoldClaimMod.RSW_THE_END_DIMENSION)));
            if (world == null) return;
            if (player == null) {
                player = new ServerPlayerEntity(server, world,
                    new GameProfile(UUID.randomUUID(), "DragonSmoke"));
                player.networkHandler = new ServerPlayNetworkHandler(server,
                    new ClientConnection(NetworkSide.SERVERBOUND), player);
                player.refreshPositionAndAngles(0.5, 80.0, 0.5, 0.0f, 0.0f);
                world.onPlayerConnected(player);
            }
            var dragons = world.getAliveEnderDragons();
            if (dragons.isEmpty()) return;
            if (dragons.size() != 1) {
                throw new IllegalStateException("RSW End spawned duplicate dragons.");
            }
            EnderDragonEntity dragon = dragons.get(0);
            if (!dragon.getCommandTags().contains("trueEnding_dragon_particlechecked")) return;
            if (dragon.getMaxHealth() != 300.0f) {
                throw new IllegalStateException("True Ending did not apply its 300 HP dragon overhaul.");
            }
            var vanillaData = server.getSaveProperties().getDragonFight();
            var fight = world.getEnderDragonFight();
            RswDragonFightService.saveFightData(world, fight.toData());
            world.setEnderDragonFight(null);
            RswDragonFightService.ensureDragonFight(world);
            if (!world.getEnderDragonFight().toData().equals(fight.toData())
                    || !server.getSaveProperties().getDragonFight().equals(vanillaData)) {
                throw new IllegalStateException("RSW fight persistence changed its state or vanilla End data.");
            }
            try {
                Files.writeString(Path.of("rsw-smoke-passed.txt"),
                    "PASS: custom RSW End spawned exactly one dragon; True Ending tag and 300 HP applied; "
                    + "fight state reloaded independently of vanilla End.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot save RSW smoke-test result", failure);
            }
            world.removePlayer(player, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            server.stop(false);
        });
    }
}
