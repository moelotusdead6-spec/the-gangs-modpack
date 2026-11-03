package com.elysium.goldclaim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.mehvahdjukaar.supplementaries.common.entities.BombEntity;
import net.mehvahdjukaar.supplementaries.common.misc.explosion.BombExplosion;
import net.mehvahdjukaar.supplementaries.common.misc.explosion.CannonBallExplosion;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;

final class ProjectileClaimSmokeTest {
    private static boolean complete;
    private static int ticks;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (complete) return;
            require(++ticks < 1200, "Projectile/celestial test timed out waiting for RSW");
            if (server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                    new net.minecraft.util.Identifier(GoldClaimMod.RSW_DIMENSION))) == null) return;
            try {
                var field = GoldClaimMod.class.getDeclaredField("claimManager");
                field.setAccessible(true);
                var claims = (ClaimManager)field.get(GoldClaimMod.getInstance());
                var owner = UUID.fromString("00000000-0000-0000-0000-000000000524");
                ServerWorld world = server.getOverworld();
                String dimension = world.getRegistryKey().getValue().toString();
                claims.removeClaimAt(owner, dimension, 160, 160, true, true);
                var result = claims.createClaim(owner, "ProjectileFixture", dimension, 160, 191, 160, 191);
                require(result.success(), "Cannot create projectile fixture claim: " + result);
                for (BombEntity.BombType type : BombEntity.BombType.values()) {
                    verify(world, type, false);
                    verify(world, type, true);
                }
                verify(world, null, false);
                verify(world, null, true);
                claims.removeClaimAt(owner, dimension, 160, 160, true, true);
                CelestialSmokeTest.verify(server);
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: all Supplementaries bomb variants and cannonballs preserve claimed blocks, "
                    + "containers and inventories; mixed inside/outside blast lists, ownerless sources, "
                    + "inside/outside origins, vertical claim coverage; unclaimed destruction retained; "
                    + "all seven native RSW lunar events activate, Wild keeps five non-blood events.\n");
                complete = true;
                server.stop(false);
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Projectile claim integration failed", failure);
            }
        });
    }

    private static void verify(ServerWorld world, BombEntity.BombType type, boolean inside) {
        BlockPos protectedPos = new BlockPos(160, 120, 175);
        BlockPos outside = protectedPos.west();
        BlockPos chest = protectedPos.up(12);
        world.getChunk(protectedPos);
        world.getChunk(outside);
        world.setBlockState(protectedPos, Blocks.STONE.getDefaultState());
        world.setBlockState(outside, Blocks.STONE.getDefaultState());
        world.setBlockState(chest, Blocks.CHEST.getDefaultState());
        var inventory = (net.minecraft.block.entity.ChestBlockEntity)world.getBlockEntity(chest);
        inventory.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 7));
        double x = inside ? 160.5 : 159.5;
        Explosion explosion = type == null
            ? new CannonBallExplosion(world, null, x, 120.5, 175.5, protectedPos, 20, 3, null)
            : new BombExplosion(world, null, null, x, 120.5, 175.5, type, Explosion.DestructionType.DESTROY);
        explosion.collectBlocksAndDamageEntities();
        // Deterministically exercise a boundary-straddling blast, independent of random ray selection.
        explosion.getAffectedBlocks().clear();
        explosion.getAffectedBlocks().addAll(List.of(protectedPos, outside, chest));
        explosion.affectWorld(false);
        String label = (type == null ? "cannonball" : type.name()) + " origin " + inside;
        require(world.getBlockState(protectedPos).isOf(Blocks.STONE), label + " destroyed claimed stone");
        require(world.getBlockState(outside).isAir(), label + " did not destroy unclaimed stone");
        require(world.getBlockEntity(chest) == inventory && inventory.getStack(0).getCount() == 7,
            label + " damaged claimed container/inventory");
        require(explosion.getAffectedBlocks().equals(List.of(outside)), label + " kept protected packet positions");
        world.removeBlock(protectedPos, false);
        world.removeBlock(chest, false);
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
