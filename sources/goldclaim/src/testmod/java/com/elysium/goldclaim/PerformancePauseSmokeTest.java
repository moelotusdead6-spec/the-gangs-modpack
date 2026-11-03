package com.elysium.goldclaim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class PerformancePauseSmokeTest {
    private record Fixture(ZombieEntity mob, ItemStack sword, ItemStack helmet, boolean protectedOriginally) {}
    private static final List<Fixture> recovering = new ArrayList<>();
    private static final List<ZombieEntity> ordinary = new ArrayList<>();
    private static int ticks;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                ticks++;
                if (ticks == 20) {
                    require(server.getCommandFunctionManager().getFunction(new Identifier("spawnanimations:main"))
                        .orElseThrow().getElements().length == 0, "Paxi did not override the animation main function");
                    require(server.getCommandFunctionManager().getFunction(new Identifier("spawnanimations:loop"))
                        .orElseThrow().getElements().length == 5, "Paxi did not override the expensive loop");
                    command(server, "scoreboard players set $hide_armor ts.sa.settings 1");
                    for (var key : List.of(World.OVERWORLD, World.NETHER, World.END)) {
                        ServerWorld world = server.getWorld(key);
                        require(world != null, "Missing fixture world " + key);
                        var player = new net.minecraft.server.network.ServerPlayerEntity(server, world,
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "PauseFixture"));
                        player.networkHandler = new net.minecraft.server.network.ServerPlayNetworkHandler(server,
                            new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player);
                        player.refreshPositionAndAngles(8.5, 110, 0.5, 0, 0);
                        player.setNoGravity(true);
                        server.getPlayerManager().getPlayerList().add(player);
                        world.onPlayerConnected(player);
                        for (int stage = 0; stage < 3; stage++) {
                            recovering.add(hidden(server, world, stage * 4, stage, stage == 2));
                        }
                        ordinary.add(mob(world, 16));
                    }
                }
                if (ticks == 30) {
                    for (Fixture fixture : recovering) {
                        ZombieEntity mob = fixture.mob();
                        ServerWorld world = (ServerWorld)mob.getWorld();
                        require(world.getEntity(mob.getUuid()) == mob && mob.isAlive(),
                            "Fixture not queryable: world=" + world.getRegistryKey().getValue()
                            + ", indexed=" + (world.getEntity(mob.getUuid()) == mob)
                            + ", alive=" + mob.isAlive() + ", removed=" + mob.isRemoved());
                    }
                    command(server, "function spawnanimations:loop");
                }
                if (ticks == 40) {
                    for (Fixture fixture : recovering) verifyRecovered(fixture);
                }
                if (ticks == 150) {
                    // Models a saved hidden mob becoming available after the initial recovery.
                    recovering.add(hidden(server, server.getWorld(World.END), 24, 1, false));
                }
                if (ticks == 260) {
                    for (Fixture fixture : recovering) verifyRecovered(fixture);
                    for (ZombieEntity mob : ordinary) {
                        require(mob.getCommandTags().stream().noneMatch(tag -> tag.startsWith("ts.sa.")),
                            "A new ordinary mob entered the paused animation system");
                        require(!mob.isInvisible() && !mob.isSilent() && !mob.isInvulnerable(),
                            "Ordinary mob state changed");
                    }
                    Files.writeString(Path.of("policy-smoke-passed.txt"),
                        "PASS: actual Paxi priority overrides Spawn Animations main/loop; no new animations; "
                        + "pending-hide, hidden and mid-animation recovery in Overworld/Nether/End; "
                        + "native equipment, position, silence and invulnerability restored; delayed recovery.\n");
                    server.stop(false);
                }
            } catch (Exception failure) {
                throw new IllegalStateException("Performance pause smoke failed", failure);
            }
        });
    }

    private static ZombieEntity mob(ServerWorld world, int x) {
        world.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL,
            new net.minecraft.util.math.ChunkPos(new BlockPos(x, 100, 0)), 3, new BlockPos(x, 100, 0));
        world.getChunk(new BlockPos(x, 100, 0));
        world.setBlockState(new BlockPos(x, 99, 0), net.minecraft.block.Blocks.STONE.getDefaultState());
        for (int y = 100; y <= 102; y++) {
            world.setBlockState(new BlockPos(x, y, 0), net.minecraft.block.Blocks.AIR.getDefaultState());
        }
        ZombieEntity mob = EntityType.HUSK.create(world);
        require(mob != null, "Cannot create fixture zombie");
        mob.refreshPositionAndAngles(x + 0.5, 100, 0.5, 0, 0);
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        mob.setPersistent();
        require(world.spawnEntity(mob), "Cannot spawn fixture zombie");
        return mob;
    }

    private static Fixture hidden(MinecraftServer server, ServerWorld world, int x, int stage,
            boolean protectedOriginally) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ZombieEntity mob = mob(world, x);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setCustomName(Text.literal("Retained sword"));
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        mob.equipStack(EquipmentSlot.MAINHAND, sword.copy());
        mob.equipStack(EquipmentSlot.HEAD, helmet.copy());
        mob.setSilent(protectedOriginally);
        mob.setInvulnerable(protectedOriginally);
        var source = server.getCommandSource().withLevel(4).withWorld(world).withEntity(mob).withPosition(mob.getPos());
        server.getCommandManager().getDispatcher().execute("function spawnanimations:internal/entity/hide", source);
        require(mob.getCommandTags().contains("ts.sa.to_hide"), "Native hide fixture failed");
        if (stage > 0) {
            server.getCommandManager().getDispatcher().execute(
                "function spawnanimations:internal/entity/ehs/hide_exec", source);
            require(mob.getCommandTags().contains("ts.sa.ehs.saved"), "Native equipment save was not exercised");
        }
        if (stage == 2) {
            server.getCommandManager().getDispatcher().execute(
                "function spawnanimations:internal/animation/dig_up/start", source);
            require(mob.getCommandTags().contains("ts.sa.verify"), "Native animation fixture failed");
            mob.refreshPositionAndAngles(mob.getX(), 97, mob.getZ(), 0, 0);
        }
        return new Fixture(mob, sword, helmet, protectedOriginally);
    }

    private static void verifyRecovered(Fixture fixture) {
        ZombieEntity mob = fixture.mob();
        require(!mob.isRemoved(), "Recovery fixture removed: " + mob.getWorld().getRegistryKey().getValue()
            + ", reason=" + mob.getRemovalReason() + ", health=" + mob.getHealth());
        for (String tag : List.of("ts.sa.to_hide", "ts.sa.to_verify", "ts.sa.verify", "ts.sa.hidden",
                "ts.sa.protected", "ts.sa.ehs.saved")) {
            require(!mob.getCommandTags().contains(tag), "Recovery left tag " + tag);
        }
        require(!mob.isInvisible(), "Recovery left a mob invisible");
        require(mob.isSilent() == fixture.protectedOriginally(), "Original silence not restored");
        require(mob.isInvulnerable() == fixture.protectedOriginally(), "Original invulnerability not restored");
        require(Math.abs(mob.getY() - 100) < 0.1, "Recovery lost original mob height: " + mob.getY());
        require(ItemStack.areEqual(mob.getMainHandStack(), fixture.sword()), "Recovery changed weapon");
        require(ItemStack.areEqual(mob.getEquippedStack(EquipmentSlot.HEAD), fixture.helmet()), "Recovery changed armor");
    }

    private static void command(MinecraftServer server, String command)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        server.getCommandManager().getDispatcher().execute(command, server.getCommandSource().withLevel(4));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
