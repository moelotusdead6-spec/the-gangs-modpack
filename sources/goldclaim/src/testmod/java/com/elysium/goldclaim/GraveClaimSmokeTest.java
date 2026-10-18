package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class GraveClaimSmokeTest {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verify(server);
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: real Universal Graves owner can use and break their grave in another player's "
                    + "untrusted claim; matching names do not bypass UUID ownership; other graves, "
                    + "chests and ordinary blocks remain protected; claim trust is unchanged.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record grave claim smoke-test result", failure);
            }
            server.stop(false);
        });
    }

    private static void verify(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        ServerPlayerEntity owner = player(server, world, "ClaimOwner");
        ServerPlayerEntity visitor = player(server, world, "GraveOwner");
        ServerPlayerEntity impostor = player(server, world, "GraveOwner");
        BlockPos pos = new BlockPos(100, 64, 100);
        String dimension = world.getRegistryKey().getValue().toString();
        ClaimManager claims;
        try {
            var field = GoldClaimMod.class.getDeclaredField("claimManager");
            field.setAccessible(true);
            claims = (ClaimManager)field.get(GoldClaimMod.getInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access grave fixture claim manager", failure);
        }
        claims.removeClaimAt(owner.getUuid(), dimension, 100, 100, true, true);
        require(claims.createClaim(owner.getUuid(), owner.getGameProfile().getName(),
            dimension, 100, 110, 100, 110).success(), "fixture claim must be created");
        require(!claims.canInteract(visitor.getUuid(), visitor.getGameProfile().getName(),
            false, dimension, 100, 100), "grave owner must not have interaction trust");

        Identifier graveId = new Identifier("universal_graves", "grave");
        require(Registries.BLOCK.containsId(graveId), "pinned Universal Graves must be installed");
        world.setBlockState(pos, Registries.BLOCK.get(graveId).getDefaultState(), 2);
        Object blockEntity = world.getBlockEntity(pos);
        require(blockEntity != null, "real grave block entity must exist");
        try {
            Class<?> graveType = Class.forName("eu.pb4.graves.grave.Grave");
            Object grave = graveType.getConstructor().newInstance();
            NbtCompound data = (NbtCompound)graveType.getMethod("writeNbt", NbtCompound.class)
                .invoke(grave, new NbtCompound());
            data.putLong("Id", 987654321L);
            data.put("GameProfile", NbtHelper.writeGameProfile(new NbtCompound(), visitor.getGameProfile()));
            graveType.getMethod("readNbt", NbtCompound.class).invoke(grave, data);
            blockEntity.getClass().getMethod("setGrave", graveType).invoke(blockEntity, grave);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize pinned Universal Graves fixture", failure);
        }
        require(use(visitor, world, pos) == ActionResult.PASS,
            "untrusted grave owner must be allowed to right-click their grave");
        require(canBreak(visitor, world, pos), "untrusted grave owner must be allowed to break their grave");
        require(use(impostor, world, pos) == ActionResult.FAIL,
            "same name with a different UUID must not bypass grave ownership");
        require(!canBreak(impostor, world, pos), "another player's grave must remain protected");
        require(use(owner, world, pos) == ActionResult.PASS, "claim owner must retain interaction access");
        require(canBreak(owner, world, pos), "claim owner must retain block access");

        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
        require(use(visitor, world, pos) == ActionResult.FAIL, "nearby inventories must remain protected");
        require(!canBreak(visitor, world, pos), "nearby chests must remain protected from breaking");
        world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
        require(use(visitor, world, pos) == ActionResult.FAIL, "ordinary block interactions must remain protected");
        require(!canBreak(visitor, world, pos), "ordinary blocks must remain protected from breaking");
        require(!claims.canModify(visitor.getUuid(), visitor.getGameProfile().getName(),
            false, dimension, 100, 100), "grave recovery must not grant claim trust");
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        claims.removeClaimAt(owner.getUuid(), dimension, 100, 100, true, true);
    }

    private static ActionResult use(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
    }

    private static boolean canBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos,
            world.getBlockState(pos), world.getBlockEntity(pos));
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name) {
        return new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
                require(!message.getString().isBlank(), "claim denials must explain the protection");
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
