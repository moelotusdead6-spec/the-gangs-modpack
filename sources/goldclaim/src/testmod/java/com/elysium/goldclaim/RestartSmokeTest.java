package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeEqualityPredicate;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SpawnHelper;

final class RestartSmokeTest {
    private static int ticks;
    private static CompletableFuture<Void> migration;
    private static RswPregenerator generator;
    private static boolean rescanning;
    private static EntityType<PathAwareEntity> roly;

    static void register() {
        roly = Registry.register(Registries.ENTITY_TYPE, new Identifier("crittersandcompanions:roly_poly"),
            EntityType.Builder.<PathAwareEntity>create((type, world) -> new PathAwareEntity(type, world) {},
                SpawnGroup.CREATURE).setDimensions(0.5f, 0.5f).build("crittersandcompanions:roly_poly"));
        FabricDefaultAttributeRegistry.register(roly, MobEntity.createMobAttributes());
        Item spear = Registry.register(Registries.ITEM, new Identifier(BannedContent.EARTHDIVE_SPEAR),
            new Item(new Item.Settings()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks > 2400) throw new IllegalStateException("Restart smoke timed out.");
            if (ticks < 20 || !RankService.ready()) return;
            if (migration == null) {
                verifyRswLandings(server);
                verifySpawns(server);
                require(new ItemStack(spear).isEmpty(), "Earthdive creation was not blocked.");
                migration = CompletableFuture.runAsync(RestartSmokeTest::verifyMigration);
                server.getOverworld().getWorldBorder().setCenter(32768, 32768);
                server.getOverworld().getWorldBorder().setSize(32);
                generator = new RswPregenerator();
            }
            if (!migration.isDone()) return;
            migration.join();
            if (!generator.prepare(server.getOverworld())) return;
            if (!rescanning) {
                rescanning = true;
                generator = new RswPregenerator();
                return;
            }
            verifyRanks(server);
            try {
                Files.writeString(Path.of("restart-smoke-passed.txt"),
                    "PASS: natural hostile gate and 95% animal gate; spawner/command spawning retained; "
                    + "Earthdive stacks removed; real LuckPerms offline user/context/expiry/track migration "
                    + "and rank colors/precedence; chunk-population RNG isolation; stricter mob caps; ground item cleanup; "
                    + "RSW collision/fluid/border landing rejection and no unsafe surface fallback; real Chunky small-border "
                    + "completion, FULL-chunk validation, ticket cleanup and saved-chunk rescan.\n");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot save smoke result", failure);
            }
            server.stop(false);
        });
    }

    private static void verifyRswLandings(MinecraftServer server) {
        var world = server.getOverworld();
        var border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(7000);
        BlockPos ground = new BlockPos(0, 200, 0);
        var stone = net.minecraft.block.Blocks.STONE.getDefaultState();
        var air = net.minecraft.block.Blocks.AIR.getDefaultState();
        world.setBlockState(ground, stone);
        world.setBlockState(ground.up(), air);
        world.setBlockState(ground.up(2), air);
        world.setBlockState(ground.east().up(), air);
        world.setBlockState(ground.east().up(2), air);
        var target = new net.minecraft.util.math.Vec3d(0.5, 201, 0.5);
        require(RswLanding.isSafe(world, target, 0.6, 1.8), "Clear saved RSW landing rejected.");
        world.setBlockState(ground.up(2), stone);
        require(!RswLanding.isSafe(world, target, 0.6, 1.8), "Saved RSW head inside wall accepted.");
        world.setBlockState(ground.up(2), air);
        world.setBlockState(ground.east().up(), stone);
        require(!RswLanding.isSafe(world, new net.minecraft.util.math.Vec3d(0.95, 201, 0.5), 0.6, 1.8),
            "Saved RSW position clipping adjacent wall accepted.");
        world.setBlockState(ground.east().up(), air);
        world.setBlockState(ground.up(), net.minecraft.block.Blocks.WATER.getDefaultState());
        require(!RswLanding.isSafe(world, target, 0.6, 1.8), "Underwater RSW landing accepted.");
        world.setBlockState(ground.up(), net.minecraft.block.Blocks.OAK_LEAVES.getDefaultState());
        world.setBlockState(ground.up(2), net.minecraft.block.Blocks.OAK_LEAVES.getDefaultState());
        var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), "LandingFixture"));
        require(GoldClaimMod.borderReturnTarget(world, player, 0.5, 0.5) == null,
            "Border return used unsafe heightmap fallback inside leaves.");
        world.setBlockState(ground.up(), air);
        world.setBlockState(ground.up(2), air);
        var landing = GoldClaimMod.borderReturnTarget(world, player, 0.5, 0.5);
        require(landing != null && RswLanding.isSafe(world, landing, 0.6, 1.8),
            "Border return failed to find clear surface.");
        border.setSize(2);
        require(!RswLanding.isSafe(world, new net.minecraft.util.math.Vec3d(0.9, 201, 0.5), 0.6, 1.8),
            "Player bounding box outside border accepted.");
        border.setSize(7000);
        world.setBlockState(ground, air);
    }

    private static void verifySpawns(MinecraftServer server) {
        var world = server.getOverworld();
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(60000000);
        try {
            var valid = SpawnHelper.class.getDeclaredMethod("isValidSpawn",
                net.minecraft.server.world.ServerWorld.class, MobEntity.class, double.class);
            valid.setAccessible(true);
            var zombie = EntityType.ZOMBIE.create(world);
            zombie.refreshPositionAndAngles(0.5, -60, 0.5, 0, 0);
            require(Boolean.FALSE.equals(valid.invoke(null, world, zombie, 100.0)), "Wild natural zombie allowed.");
            require(new net.minecraft.world.spawner.PhantomSpawner().spawn(world, true, true) == 0, "Wild phantom spawn allowed.");
            require(new net.minecraft.world.spawner.PatrolSpawner().spawn(world, true, true) == 0, "Wild patrol spawn allowed.");
            require(new net.minecraft.village.ZombieSiegeManager().spawn(world, true, true) == 0, "Wild siege spawn allowed.");
            var generation = java.util.Arrays.stream(SpawnHelper.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("goldclaim$generationSpawn")).findFirst().orElseThrow();
            generation.setAccessible(true);
            int allowed = 0;
            var populationRandom = net.minecraft.util.math.random.Random.create(12345L);
            world.random.setSeed(12345L);
            for (int index = 0; index < 1000; index++) {
                if (generation.invoke(null, roly, world, world,
                        world.getBiome(new BlockPos(0, -60, 0)), new net.minecraft.util.math.ChunkPos(0, 0),
                        populationRandom) != null) allowed++;
            }
            require(world.random.nextLong() == net.minecraft.util.math.random.Random.create(12345L).nextLong(),
                "Chunk population consumed the server world's random instead of its own random.");
            require(allowed >= 20 && allowed <= 80, "Animal spawn acceptance outside expected 5% range: " + allowed);
            for (SpawnReason reason : new SpawnReason[]{SpawnReason.SPAWNER, SpawnReason.COMMAND, SpawnReason.SPAWN_EGG}) {
                BlockPos spawn = world.getSpawnPos();
                world.getChunk(spawn);
                var spawned = EntityType.ZOMBIE.spawn(world, spawn, reason);
                require(spawned != null && world.getEntity(spawned.getUuid()) == spawned,
                    reason + " was incorrectly blocked: entity=" + spawned + ", spawn=" + spawn
                        + ", registered=" + (spawned == null ? null : world.getEntity(spawned.getUuid())));
                spawned.discard();
            }
            require(SpawnGroup.MONSTER.getCapacity() == 61 && SpawnGroup.CREATURE.getCapacity() == 9
                && SpawnGroup.WATER_AMBIENT.getCapacity() == 17 && SpawnGroup.MISC.getCapacity() == -1,
                "Stricter mob caps were not applied: monster=" + SpawnGroup.MONSTER.getCapacity());
            BlockPos dropAt = world.getSpawnPos();
            var drop = new net.minecraft.entity.ItemEntity(world, dropAt.getX() + 0.5, dropAt.getY() + 1, dropAt.getZ() + 0.5,
                new ItemStack(net.minecraft.item.Items.DIRT));
            require(world.spawnEntity(drop), "Could not drop a test item.");
            var keep = EntityType.PIG.spawn(world, dropAt, SpawnReason.COMMAND);
            require(GroundCleanup.clear(server) >= 1 && drop.isRemoved(), "Ground cleanup left a dropped item.");
            require(keep != null && !keep.isRemoved(), "Ground cleanup removed a non-item entity.");
            keep.discard();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot verify injected natural spawning hooks", failure);
        }
    }

    private static void verifyMigration() {
        var api = LuckPermsProvider.get();
        var groups = api.getGroupManager();
        var existing = groups.createAndLoadGroup("drifters").join();
        existing.data().clear();
        groups.saveGroup(existing).join();
        var old = groups.createAndLoadGroup("player").join();
        var permission = Node.builder("fixture.preserve").value(false).withContext("server", "fixture")
            .expiry(java.time.Instant.ofEpochSecond(java.time.Instant.now().getEpochSecond() + 3600)).build();
        old.data().add(permission);
        old.data().add(InheritanceNode.builder("default").build());
        groups.saveGroup(old).join();
        var staff = groups.createAndLoadGroup("fixture_staff").join();
        staff.data().clear();
        var parent = InheritanceNode.builder("player").value(false).withContext("world", "fixture")
            .expiry(java.time.Instant.ofEpochSecond(java.time.Instant.now().getEpochSecond() + 7200)).build();
        staff.data().add(parent);
        groups.saveGroup(staff).join();
        var id = UUID.fromString("00000000-0000-0000-0000-000000000144");
        var user = api.getUserManager().loadUser(id, "grimmnm").join();
        user.data().add(InheritanceNode.builder("player").build());
        user.setPrimaryGroup("player");
        api.getUserManager().saveUser(user).join();
        var track = api.getTrackManager().createAndLoadTrack("fixture").join();
        track.clearGroups();
        track.appendGroup(old);
        api.getTrackManager().saveTrack(track).join();
        RankService.migrate(api);
        require(groups.loadGroup("player").join().isEmpty(), "Player group still exists.");
        var drifters = groups.loadGroup("drifters").join().orElseThrow();
        require(drifters.getNodes().stream().anyMatch(node -> node.equals(permission, NodeEqualityPredicate.EXACT)),
            "Group permission value/context/expiry changed.");
        require(staff.getNodes().stream().anyMatch(node ->
            node.equals(parent.toBuilder().group("drifters").build(), NodeEqualityPredicate.EXACT)),
            "Negative scoped temporary inheritance changed.");
        require(user.getNodes().stream().anyMatch(node -> node.getKey().equals("group.drifters")), "Offline user not migrated.");
        require(user.getNodes().stream().anyMatch(node -> node.getKey().equals("group.gang")), "Named Gang member not persisted.");
        require(!user.getPrimaryGroup().equals("player"), "Offline primary group not migrated.");
        require(track.getGroups().equals(java.util.List.of("drifters")), "Promotion track not migrated.");
    }

    private static void verifyRanks(MinecraftServer server) {
        var player = new ServerPlayerEntity(server, server.getOverworld(),
            new GameProfile(UUID.randomUUID(), "NewDrifter"));
        RankService.apply(player, server);
        var team = server.getScoreboard().getPlayerTeam("NewDrifter");
        require(team != null && team.getName().equals("03_drifters")
            && team.getColor() == net.minecraft.util.Formatting.GRAY, "New-player rank/color incorrect.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
