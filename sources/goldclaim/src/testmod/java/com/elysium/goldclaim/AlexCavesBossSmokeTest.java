package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

/**
 * Exercises every Alex's Caves boss block-damage path on a real server with mobGriefing ON.
 * GOLDCLAIM_ALEXCAVES_SMOKE=protected requires zero block changes; =control (old GoldClaim) records
 * how many blocks each stimulus changes, proving the stimuli really grief without the protection.
 */
final class AlexCavesBossSmokeTest {
    private static final int FLOOR = -61;
    private static final int RADIUS = 26;
    private static final int TOP = -25;
    private static final int NATURAL_TICKS = 600;
    private static final BlockPos HEALTH_CENTER = new BlockPos(-400, -60, 400);
    private static final Map<String, Float> HEALTH = Map.of(
        "tremorzilla", 75_000f, "luxtructosaurus", 50_000f, "hullbreaker", 25_000f, "forsaken", 25_000f);

    private static boolean protectedMode;
    private static final Map<String, Arena> ARENAS = new LinkedHashMap<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<Entity> NATURAL = new ArrayList<>();
    private static int ticks = -2;
    private static int arenaCount;
    private static BlockPos victimMarker;
    private static boolean victimPlaced;

    private record Arena(BlockPos center, BlockState[] before) {}

    static void register() {
        protectedMode = "protected".equals(System.getenv("GOLDCLAIM_ALEXCAVES_SMOKE"));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getCommandTags().contains("goldclaim_victim")) {
                victimPlaced = entity.getWorld().setBlockState(victimMarker, Blocks.GOLD_BLOCK.getDefaultState());
            }
        });
        // Run after every SERVER_STARTED hook: AttributeFix lifts the 1024 max-health cap in its own hook.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ticks == -2) {
                ticks = -1;
                try {
                    setup(server);
                    ticks = 0;
                } catch (Throwable failure) {
                    finish(server, failure);
                }
                return;
            }
            if (ticks < 0) return;
            try {
                tickNatural(server.getOverworld(), ++ticks);
                if (ticks >= NATURAL_TICKS) {
                    ticks = -1;
                    if (protectedMode) verifyHealth(server.getOverworld());
                    verifyEnd(server.getOverworld());
                    finish(server, null);
                }
            } catch (Throwable failure) {
                ticks = -1;
                finish(server, failure);
            }
        });
    }

    private static void setup(MinecraftServer server) throws Exception {
        ServerWorld world = server.getOverworld();
        server.setDifficulty(Difficulty.NORMAL, true);
        GameRules rules = world.getGameRules();
        rules.get(GameRules.DO_MOB_GRIEFING).set(true, server);
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, server);
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        rules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
        rules.get(GameRules.DO_FIRE_TICK).set(false, server);
        world.setTimeOfDay(18000);
        REPORT.add("mode=" + (protectedMode ? "protected" : "control") + " mobGriefing="
            + rules.getBoolean(GameRules.DO_MOB_GRIEFING));

        if (protectedMode) {
            verifyGuard(world);
            // Entities only fire ENTITY_LOAD once their chunk is tracked, so check health after the natural phase.
            forceChunks(world, HEALTH_CENTER);
        }

        // Tremorzilla: tail/bite breakBlocksAround (with a nuclear bomb in range), bounding-box crush, atomic beam.
        Arena arena = arena(world, "tremorzilla_around", false);
        MobEntity tremor = boss(world, "tremorzilla", arena.center());
        BlockPos nuke = arena.center().add(5, 1, 0);
        world.setBlockState(nuke, block("nuclear_bomb"));
        resnapshot(world, "tremorzilla_around");
        for (int i = 0; i < 4; i++) {
            invoke(tremor, "breakBlocksAround", new Class<?>[] {Vec3d.class, float.class, boolean.class, boolean.class,
                float.class}, Vec3d.ofCenter(nuke), 4.0f, false, true, 1.0f);
            invoke(tremor, "breakBlocksAround", new Class<?>[] {Vec3d.class, float.class, boolean.class, boolean.class,
                float.class}, Vec3d.ofCenter(arena.center().add(-6, 1, 0)), 3.0f, false, false, 0.6f);
        }
        int bombs = count(world, arena.center(), "nuclear_bomb");
        REPORT.add("tremorzilla nuclear_bomb entities spawned=" + bombs);
        if (protectedMode && bombs != 0) FAILURES.add("Tremorzilla beam/claws armed a nuclear bomb");
        discardAll(world, arena.center(), "nuclear_bomb");
        measure(world, "tremorzilla_around");

        arena = arena(world, "tremorzilla_bbox", false);
        tremor = boss(world, "tremorzilla", arena.center());
        embed(world, tremor);
        resnapshot(world, "tremorzilla_bbox");
        invoke(tremor, "breakBlocksInBoundingBox", new Class<?>[] {float.class}, 0.1f);
        measure(world, "tremorzilla_bbox");

        arena = arena(world, "tremorzilla_beam", false);
        tremor = boss(world, "tremorzilla", arena.center());
        world.tickEntity(tremor);
        BlockPos wall = arena.center().add(18, 2, 0);
        for (int i = 0; i < 12; i++) {
            setField(tremor, "beamServerTarget", Vec3d.ofCenter(wall));
            setField(tremor, "beamTime", 0);
            invoke(tremor, "setMaxBeamBreakLength", new Class<?>[] {float.class}, 100.0f);
            invoke(tremor, "tickBreath", new Class<?>[0]);
        }
        measure(world, "tremorzilla_beam");
        if (protectedMode && count(world, arena.center(), "nuclear_bomb") != 0) FAILURES.add("beam armed a bomb");

        // Luxtructosaurus: fissure stomp, crush stomp, fire breath, standing in water / colliding.
        arena = arena(world, "lux_fissure", false);
        MobEntity lux = boss(world, "luxtructosaurus", arena.center());
        world.tickEntity(lux);
        resnapshot(world, "lux_fissure");
        for (int progress : new int[] {19, 17, 15, 13}) {
            setField(lux, "stompMakesFissures", true);
            setField(lux, "postStopTicks", progress);
            invoke(lux, "tickStompAttack", new Class<?>[0]);
        }
        for (int width = 2; width <= 14; width += 2) {
            invoke(lux, "placeFissureRing", new Class<?>[] {int.class, int.class, int.class}, width,
                arena.center().getX(), arena.center().getZ());
        }
        measure(world, "lux_fissure");

        arena = arena(world, "lux_crush", false);
        lux = boss(world, "luxtructosaurus", arena.center());
        world.tickEntity(lux);
        resnapshot(world, "lux_crush");
        setField(lux, "stompMakesFissures", false);
        setField(lux, "postStopTicks", 20);
        invoke(lux, "tickStompAttack", new Class<?>[0]);
        for (int width = 2; width <= 12; width += 2) {
            invoke(lux, "crushBlocksInRing", new Class<?>[] {int.class, int.class, int.class, float.class}, width,
                arena.center().getX(), arena.center().getZ(), 0.2f);
        }
        measure(world, "lux_crush");

        arena = arena(world, "lux_breath", false);
        lux = boss(world, "luxtructosaurus", arena.center());
        world.tickEntity(lux);
        resnapshot(world, "lux_breath");
        for (int i = 0; i < 60; i++) invoke(lux, "burnWithBreath", new Class<?>[] {float.class}, 20.0f);
        measure(world, "lux_breath");
        if (protectedMode && count(world, arena.center(), Blocks.FIRE) != 0) FAILURES.add("Lux breath placed fire");

        arena = arena(world, "lux_water", true);
        lux = boss(world, "luxtructosaurus", arena.center().down(2));
        resnapshot(world, "lux_water");
        for (int i = 0; i < 20; i++) world.tickEntity(lux);
        measure(world, "lux_water");

        // Tephra (Lux's volcanic bombs).
        arena = arena(world, "tephra", false);
        Entity tephra = Registries.ENTITY_TYPE.get(new Identifier("alexscaves", "tephra")).create(world);
        tephra.refreshPositionAndAngles(arena.center().getX() + 0.5, FLOOR + 1, arena.center().getZ() + 0.5, 0, 0);
        world.spawnEntity(tephra);
        resnapshot(world, "tephra");
        invoke(tephra, "explode", new Class<?>[0]);
        measure(world, "tephra");

        // Hullbreaker head ram.
        arena = arena(world, "hullbreaker", true);
        MobEntity hull = boss(world, "hullbreaker", arena.center().down(2));
        world.tickEntity(hull);
        Entity head = (Entity)getField(hull, "headPart");
        Box headBox = head.getBoundingBox().expand(1.2);
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(headBox.minX, headBox.minY - 1, headBox.minZ),
                BlockPos.ofFloored(headBox.maxX, headBox.maxY + 1, headBox.maxZ))) {
            if (inArena(arena.center(), pos)) world.setBlockState(pos, Blocks.DIRT.getDefaultState());
        }
        resnapshot(world, "hullbreaker");
        ServerPlayerEntity target = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), "HullTarget"));
        target.refreshPositionAndAngles(head.getX(), head.getY(), head.getZ(), 0, 0);
        hull.setTarget(target);
        for (int i = 0; i < 6; i++) {
            setField(hull, "blockBreakCooldown", 0);
            invoke(hull, "breakBlock", new Class<?>[0]);
        }
        hull.setTarget(null);
        measure(world, "hullbreaker");

        // Forsaken: suffocation escape and direct breakBlock attribution.
        arena = arena(world, "forsaken", false);
        MobEntity forsaken = boss(world, "forsaken", arena.center());
        embed(world, forsaken);
        resnapshot(world, "forsaken");
        setField(forsaken, "destroyBlocksTick", 1);
        world.tickEntity(forsaken);
        BlockPos dirt = arena.center().add(3, 1, 0);
        world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
        resnapshot(world, "forsaken");
        setField(forsaken, "destroyBlocksTick", 1);
        world.tickEntity(forsaken);
        boolean broke = world.breakBlock(dirt, true, forsaken);
        REPORT.add("forsaken world.breakBlock(pos,true,forsaken) returned " + broke);
        if (protectedMode && broke) FAILURES.add("Forsaken-attributed breakBlock succeeded");
        measure(world, "forsaken");

        // Magnetron: forming steals blocks; death drops body blocks + heart as falling blocks.
        arena = arena(world, "magnetron", false);
        MobEntity magnetron = boss(world, "magnetron", arena.center());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    world.setBlockState(arena.center().add(dx, 1, dz), Blocks.IRON_BLOCK.getDefaultState());
                }
            }
        }
        world.tickEntity(magnetron);
        resnapshot(world, "magnetron");
        invoke(magnetron, "startForming", new Class<?>[0]);
        measure(world, "magnetron_forming");
        ZombieEntity killer = EntityType.ZOMBIE.create(world);
        killer.refreshPositionAndAngles(arena.center().getX() + 0.5, FLOOR + 1, arena.center().getZ() + 3.5, 0, 0);
        world.spawnEntity(killer);
        magnetron.damage(world.getDamageSources().mobAttack(killer), 100_000f);
        for (int i = 0; i < 100 && !magnetron.isRemoved(); i++) world.tickEntity(magnetron);
        killer.discard();
        int falling = world.getEntitiesByClass(FallingBlockEntity.class, box(arena.center()).expand(16), e -> true).size();
        int hearts = 0;
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box(arena.center()).expand(16), e -> true)) {
            if (Registries.ITEM.getId(item.getStack().getItem()).equals(new Identifier("alexscaves", "heart_of_iron"))) {
                hearts += item.getStack().getCount();
            }
        }
        REPORT.add("magnetron death: falling blocks=" + falling + " heart_of_iron items=" + hearts
            + " droppedHeart=" + getField(magnetron, "droppedHeart") + " removed=" + magnetron.isRemoved()
            + " items=" + world.getEntitiesByClass(ItemEntity.class, box(arena.center()).expand(16), e -> true).stream()
                .map(e -> e.getStack() + "@" + e.getBlockPos().toShortString()).toList());
        if (!magnetron.isRemoved()) FAILURES.add("Magnetron did not finish dying");
        if (protectedMode && falling != 0) FAILURES.add("Magnetron death spawned " + falling + " falling blocks");
        if (protectedMode && hearts != 1) FAILURES.add("Magnetron must drop exactly one Heart of Iron item, got " + hearts);
        for (FallingBlockEntity block : world.getEntitiesByClass(FallingBlockEntity.class, box(arena.center()).expand(16), e -> true)) {
            block.discard();
        }
        measure(world, "magnetron_death");

        // Natural AI over real server ticks with live targets.
        natural(world, "natural_tremorzilla", "tremorzilla", false, true);
        natural(world, "natural_lux", "luxtructosaurus", true, false);
        natural(world, "natural_forsaken", "forsaken", false, true);
        natural(world, "natural_magnetron", "magnetron", false, false);
        natural(world, "natural_hullbreaker", "hullbreaker", true, false);
    }

    private static void natural(ServerWorld world, String name, String type, boolean water, boolean embedded) {
        Arena arena = arena(world, name, water);
        MobEntity boss = boss(world, type, water ? arena.center().down(2) : arena.center());
        if (type.equals("magnetron")) {
            for (int dx = -3; dx <= 3; dx += 6) {
                for (int dz = -3; dz <= 3; dz++) world.setBlockState(arena.center().add(dx, 1, dz), Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        if (embedded) embed(world, boss);
        resnapshot(world, name);
        boss.addCommandTag("goldclaim_natural:" + name);
        NATURAL.add(boss);
    }

    private static void tickNatural(ServerWorld world, int tick) {
        if (tick % 20 != 1) return;
        for (Entity entity : NATURAL) {
            if (!(entity instanceof MobEntity boss) || !boss.isAlive()) continue;
            LivingEntity target = boss.getTarget();
            if (target == null || !target.isAlive()) {
                ZombieEntity zombie = EntityType.ZOMBIE.create(world);
                zombie.refreshPositionAndAngles(boss.getX() + 8, FLOOR + 1, boss.getZ() + 4, 0, 0);
                zombie.setPersistent();
                world.spawnEntity(zombie);
                boss.setTarget(zombie);
            }
        }
    }

    private static void verifyEnd(ServerWorld world) throws Exception {
        for (Entity entity : NATURAL) {
            String name = entity.getCommandTags().stream().filter(t -> t.startsWith("goldclaim_natural:"))
                .findFirst().orElseThrow().substring("goldclaim_natural:".length());
            REPORT.add(name + " ticked age=" + entity.age + " alive=" + entity.isAlive());
            if (entity.age < NATURAL_TICKS / 2) FAILURES.add(name + " did not tick naturally (age " + entity.age + ")");
            measure(world, name);
        }
        if (protectedMode) {
            for (Map.Entry<String, Arena> entry : ARENAS.entrySet()) {
                if (entry.getKey().startsWith("natural_")) continue;
                int changed = diff(world, entry.getValue(), new ArrayList<>());
                if (changed != 0) FAILURES.add(entry.getKey() + " changed " + changed + " blocks after natural ticking");
            }
            Object actor = protection("protectedActor", new Class<?>[0]);
            if (actor != null) FAILURES.add("protected actor leaked after ticking: " + actor);
        }
    }

    private static void verifyGuard(ServerWorld world) throws Exception {
        BlockPos center = new BlockPos(-400, FLOOR, -400);
        forceChunks(world, center);
        BlockPos dirt = center.up();
        BlockPos air = center.up(2);
        world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
        world.setBlockState(air, Blocks.AIR.getDefaultState());
        MobEntity lux = boss(world, "luxtructosaurus", center.add(20, 1, 0));
        MobEntity tremor = boss(world, "tremorzilla", center.add(-20, 1, 0));
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        zombie.refreshPositionAndAngles(center.getX() + 0.5, FLOOR + 1, center.getZ() + 6.5, 0, 0);
        world.spawnEntity(zombie);
        boolean[] results = new boolean[8];
        protection("whileActing", new Class<?>[] {Entity.class, Runnable.class}, lux, (Runnable)() -> {
            results[0] = world.setBlockState(dirt, Blocks.AIR.getDefaultState());
            results[1] = world.breakBlock(dirt, true);
            results[2] = world.removeBlock(dirt, false);
            results[3] = world.setBlockState(air, block("tremorzilla_egg"));
        });
        require(!results[0] && !results[1] && !results[2] && !results[3], "boss tick context must block every block change");
        require(world.getBlockState(dirt).isOf(Blocks.DIRT), "dirt must survive the boss context");
        protection("whileActing", new Class<?>[] {Entity.class, Runnable.class}, tremor, (Runnable)() -> {
            results[4] = world.setBlockState(air, block("tremorzilla_egg"));
            results[5] = world.setBlockState(dirt, block("tremorzilla_egg"));
        });
        require(results[4], "Tremorzilla must still be able to lay its egg into air");
        require(!results[5], "Tremorzilla egg must not replace solid blocks");
        world.setBlockState(air, Blocks.AIR.getDefaultState());
        protection("whileActing", new Class<?>[] {Entity.class, Runnable.class}, zombie, (Runnable)() ->
            results[6] = world.setBlockState(air, Blocks.STONE.getDefaultState()));
        require(results[6], "non-boss mobs must keep normal block behaviour");
        world.setBlockState(air, Blocks.AIR.getDefaultState());
        require(world.breakBlock(dirt, false, zombie), "zombie-attributed block breaking must still work");

        victimMarker = center.up(3);
        world.setBlockState(victimMarker, Blocks.AIR.getDefaultState());
        zombie.addCommandTag("goldclaim_victim");
        protection("whileActing", new Class<?>[] {Entity.class, Runnable.class}, lux, (Runnable)() ->
            results[7] = zombie.damage(world.getDamageSources().mobAttack(lux), 100_000f));
        require(results[7] && !zombie.isAlive(), "victim must die from boss damage");
        require(victimPlaced && world.getBlockState(victimMarker).isOf(Blocks.GOLD_BLOCK),
            "death handlers (graves) must be able to place blocks when a boss kills a victim");
        require(protection("protectedActor", new Class<?>[0]) == null, "guard must be cleared after acting");
        lux.discard();
        tremor.discard();
        REPORT.add("guard: boss context blocks setBlockState/breakBlock/removeBlock; egg-into-air allowed; "
            + "non-boss context allowed; boss-killed victim death handler placed a block");
    }

    private static void verifyHealth(ServerWorld world) {
        BlockPos center = HEALTH_CENTER;
        for (Map.Entry<String, Float> entry : HEALTH.entrySet()) {
            MobEntity boss = boss(world, entry.getKey(), center);
            float target = entry.getValue();
            require(Math.abs(boss.getMaxHealth() - target) < 0.5f, entry.getKey() + " max health " + boss.getMaxHealth());
            require(Math.abs(boss.getHealth() - target) < 0.5f, entry.getKey() + " spawn health " + boss.getHealth());
            boss.setHealth(target / 2);
            Entity current = boss;
            for (int reload = 0; reload < 3; reload++) {
                NbtCompound nbt = new NbtCompound();
                require(current.saveNbt(nbt), "cannot save " + entry.getKey());
                current.discard();
                current = EntityType.loadEntityWithPassengers(nbt, world, e -> e);
                require(current != null && world.spawnNewEntityAndPassengers(current), "cannot reload " + entry.getKey());
                LivingEntity living = (LivingEntity)current;
                EntityAttributeInstance attribute = living.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                require(Math.abs(living.getMaxHealth() - target) < 0.5f,
                    entry.getKey() + " reload " + reload + " max health " + living.getMaxHealth());
                require(Math.abs(living.getHealth() - target / 2) < 0.5f,
                    entry.getKey() + " reload " + reload + " health " + living.getHealth());
                require(attribute.getModifiers().size() == 1,
                    entry.getKey() + " has " + attribute.getModifiers().size() + " health modifiers after reload");
            }
            REPORT.add("health " + entry.getKey() + " max=" + ((LivingEntity)current).getMaxHealth()
                + " half-health preserved across 3 save/reload cycles");
            current.discard();
        }
        MobEntity magnetron = boss(world, "magnetron", center);
        REPORT.add("health magnetron max=" + magnetron.getMaxHealth() + " (unchanged)");
        magnetron.discard();
    }

    private static Arena arena(ServerWorld world, String name, boolean water) {
        BlockPos center = new BlockPos(160 * ++arenaCount, FLOOR, 0);
        forceChunks(world, center);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int y = FLOOR - 2; y <= TOP; y++) {
                    world.setBlockState(new BlockPos(center.getX() + dx, y, center.getZ() + dz),
                        y <= FLOOR ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        for (int i = -18; i <= 18; i++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlockState(center.add(i, y, -18), Blocks.COBBLESTONE.getDefaultState(), 2);
                world.setBlockState(center.add(i, y, 18), Blocks.COBBLESTONE.getDefaultState(), 2);
                world.setBlockState(center.add(-18, y, i), Blocks.COBBLESTONE.getDefaultState(), 2);
                world.setBlockState(center.add(18, y, i), Blocks.COBBLESTONE.getDefaultState(), 2);
            }
        }
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                if (water && Math.abs(dx) <= 6 && Math.abs(dz) <= 6) continue;
                for (int y = 1; y <= 4; y++) world.setBlockState(center.add(dx, y, dz), Blocks.DIRT.getDefaultState(), 2);
            }
        }
        BlockState leaves = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
        for (int y = 1; y <= 5; y++) world.setBlockState(center.add(10, y, -10), Blocks.OAK_LOG.getDefaultState(), 2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    world.setBlockState(center.add(10 + dx, 5, -10 + dz),
                        leaves.with(LeavesBlock.DISTANCE, Math.abs(dx) + Math.abs(dz)), 2);
                }
            }
        }
        if (water) {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    for (int y = -2; y <= 0; y++) world.setBlockState(center.add(dx, y, dz), Blocks.WATER.getDefaultState(), 2);
                }
            }
        }
        Arena arena = new Arena(center, snapshot(world, center));
        ARENAS.put(name, arena);
        return arena;
    }

    private static void resnapshot(ServerWorld world, String name) {
        Arena old = ARENAS.get(name);
        ARENAS.put(name, new Arena(old.center(), snapshot(world, old.center())));
    }

    private static void measure(ServerWorld world, String name) {
        Arena arena = ARENAS.get(name.equals("magnetron_forming") || name.equals("magnetron_death") ? "magnetron" : name);
        List<String> samples = new ArrayList<>();
        int changed = diff(world, arena, samples);
        REPORT.add(name + " changed_blocks=" + changed + (samples.isEmpty() ? "" : " e.g. " + samples));
        if (protectedMode && changed != 0) FAILURES.add(name + " changed " + changed + " blocks: " + samples);
    }

    private static int diff(ServerWorld world, Arena arena, List<String> samples) {
        int index = 0;
        int changed = 0;
        BlockPos center = arena.center();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int y = FLOOR - 2; y <= TOP; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    BlockState now = world.getBlockState(pos);
                    if (now != arena.before()[index++]) {
                        changed++;
                        if (samples.size() < 4) {
                            samples.add(pos.toShortString() + " " + arena.before()[index - 1].getBlock() + "->" + now.getBlock());
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static BlockState[] snapshot(ServerWorld world, BlockPos center) {
        BlockState[] states = new BlockState[(2 * RADIUS + 1) * (2 * RADIUS + 1) * (TOP - FLOOR + 3)];
        int index = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int y = FLOOR - 2; y <= TOP; y++) {
                    states[index++] = world.getBlockState(new BlockPos(center.getX() + dx, y, center.getZ() + dz));
                }
            }
        }
        return states;
    }

    private static boolean inArena(BlockPos center, BlockPos pos) {
        return Math.abs(pos.getX() - center.getX()) <= RADIUS && Math.abs(pos.getZ() - center.getZ()) <= RADIUS
            && pos.getY() >= FLOOR - 2 && pos.getY() <= TOP;
    }

    private static void embed(ServerWorld world, Entity entity) {
        Box box = entity.getBoundingBox().expand(1.0);
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, Math.max(box.minY, FLOOR + 1), box.minZ),
                BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
            if (world.getBlockState(pos).isAir()) world.setBlockState(pos.toImmutable(), Blocks.DIRT.getDefaultState(), 2);
        }
    }

    private static void forceChunks(ServerWorld world, BlockPos center) {
        for (int cx = (center.getX() >> 4) - 2; cx <= (center.getX() >> 4) + 2; cx++) {
            for (int cz = (center.getZ() >> 4) - 2; cz <= (center.getZ() >> 4) + 2; cz++) world.setChunkForced(cx, cz, true);
        }
    }

    private static MobEntity boss(ServerWorld world, String id, BlockPos pos) {
        Entity entity = Registries.ENTITY_TYPE.get(new Identifier("alexscaves", id)).create(world);
        require(entity instanceof MobEntity, "cannot create alexscaves:" + id);
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
        ((MobEntity)entity).setPersistent();
        require(world.spawnEntity(entity), "cannot spawn alexscaves:" + id);
        return (MobEntity)entity;
    }

    private static BlockState block(String id) {
        Identifier identifier = new Identifier("alexscaves", id);
        require(Registries.BLOCK.containsId(identifier), "missing block " + identifier);
        return Registries.BLOCK.get(identifier).getDefaultState();
    }

    private static Box box(BlockPos center) {
        return new Box(center).expand(RADIUS, 40, RADIUS);
    }

    private static int count(ServerWorld world, BlockPos center, String type) {
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(new Identifier("alexscaves", type));
        return world.getEntitiesByClass(Entity.class, box(center), e -> e.getType() == entityType).size();
    }

    private static int count(ServerWorld world, BlockPos center, net.minecraft.block.Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-RADIUS, -2, -RADIUS), center.add(RADIUS, TOP - FLOOR, RADIUS))) {
            if (world.getBlockState(pos).isOf(block)) found++;
        }
        return found;
    }

    private static void discardAll(ServerWorld world, BlockPos center, String type) {
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(new Identifier("alexscaves", type));
        world.getEntitiesByClass(Entity.class, box(center), e -> e.getType() == entityType).forEach(Entity::discard);
    }

    private static Object protection(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = Class.forName("com.elysium.goldclaim.AlexCavesProtection").getMethod(name, types);
        return method.invoke(null, args);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static Object getField(Object target, String name) throws Exception {
        return field(target, name).get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        field(target, name).set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void finish(MinecraftServer server, Throwable failure) {
        if (failure != null) {
            FAILURES.add("exception: " + failure);
            failure.printStackTrace();
        }
        StringBuilder text = new StringBuilder();
        for (String line : REPORT) text.append(line).append('\n');
        for (String line : FAILURES) text.append("FAIL: ").append(line).append('\n');
        String file = !protectedMode ? "alexcaves-control-result.txt"
            : FAILURES.isEmpty() ? "alexcaves-boss-smoke-passed.txt" : "alexcaves-boss-smoke-failed.txt";
        if (protectedMode && FAILURES.isEmpty()) text.append("PASS: no Alex's Caves boss changed any block.\n");
        try {
            Files.writeString(Path.of(file), text.toString());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        System.out.println("[AlexCavesBossSmoke]\n" + text);
        server.stop(false);
    }
}
