package com.gangs.gangstrials.smoke;

import com.drd.trickytrialsbackport.block.entity.trialspawner.TrialSpawnerBlockEntity;
import com.gangs.gangstrials.TrialSpawnerFixes;
import com.gangs.gangstrials.smoke.mixin.WorldRandomAccessor;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import me.isaiah.multiworld.MultiworldMod;
import me.isaiah.multiworld.command.CreateCommand;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.structure.Structure;
import net.zestyblaze.lootr.api.blockentity.ILootBlockEntity;
import net.zestyblaze.lootr.block.entities.LootrInventoryBlockEntity;
import net.zestyblaze.lootr.config.ConfigManager;

public final class BootsTrialsSmoke implements DedicatedServerModInitializer {
    public static boolean collectExpectedSpawners;
    public static final Set<BlockPos> expectedSpawners = new LinkedHashSet<>();
    private static final Identifier BOOTS = new Identifier("gangsboots", "boots_of_swiftness");
    private static final Identifier DUNGEON = new Identifier("minecraft", "chests/simple_dungeon");
    private boolean ran;

    @Override
    public void onInitializeServer() {
        if (Files.exists(Path.of("RSW_AUDIT_LOCAL_ONLY"))) {
            RswStructureAudit.register();
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) return;
            ran = true;
            try {
                require(Files.exists(Path.of("LOCAL_TEST_ONLY")), "Refusing to run outside an explicitly marked local fixture");
                System.out.println("LOCAL_BOOTS_TRIALS_START");
                Map<String, Object> report = verify(server);
                Files.writeString(Path.of("boots-trials-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                System.out.println("BOOTS_TRIALS_PASS " + report.get("randomSample"));
                server.stop(false);
            } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                throw new IllegalStateException("Local boots/trials test failed", failure);
            }
        });
    }

    private static Map<String, Object> verify(MinecraftServer server)
            throws IOException, com.mojang.brigadier.exceptions.CommandSyntaxException {
        require(server.getDataPackManager().getEnabledNames().contains("gangs_fixes"),
            "Current pack's Paxi fixes are not enabled in the fixture");
        for (String[] dimension : List.of(
                new String[]{"hub", "NORMAL"}, new String[]{"pvp", "NORMAL"},
                new String[]{"rsw", "NORMAL"}, new String[]{"rsw_nether", "NETHER"},
                new String[]{"rsw_the_end", "THE_END"})) {
            var generator = CreateCommand.get_chunk_gen(server, "FLAT");
            var type = CreateCommand.get_dim_id(dimension[1]);
            require(generator != null && type != null, "Unsupported fixture world configuration");
            var world = MultiworldMod.create_world("multiworld:" + dimension[0], type,
                generator, server.getSaveProperties().getDifficulty(), 400L);
            require(world != null, "Cannot create fixture dimension " + dimension[0]);
            CreateCommand.make_config(world, dimension[1], 400L, "FLAT");
        }
        List<ServerWorld> worlds = new ArrayList<>();
        server.getWorlds().forEach(worlds::add);
        worlds.sort(Comparator.comparing(world -> world.getRegistryKey().getValue().toString()));
        require(worlds.stream().anyMatch(world -> world.getRegistryKey().getValue().toString().equals("multiworld:rsw")),
            "RSW fixture dimension missing");
        List<Block> containers = new ArrayList<>();
        Map<String, String> excluded = new LinkedHashMap<>();
        for (Block block : Registries.BLOCK) {
            String path = Registries.BLOCK.getId(block).getPath();
            if (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock)
                    && !path.contains("chest") && !path.contains("barrel")) continue;
            if (!(block instanceof BlockEntityProvider provider)) continue;
            BlockEntity entity = provider.createBlockEntity(BlockPos.ORIGIN, block.getDefaultState());
            if (entity instanceof LootrInventoryBlockEntity) {
                excluded.put(Registries.BLOCK.getId(block).toString(), "Copies reference contents; does not generate a loot table");
            } else if (!(entity instanceof LootableContainerBlockEntity)) {
                excluded.put(Registries.BLOCK.getId(block).toString(), "No loot-table-bearing container entity");
            } else if (!(entity instanceof ILootBlockEntity) && ConfigManager.replacement(block.getDefaultState()) == null) {
                excluded.put(Registries.BLOCK.getId(block).toString(), "Not converted by the pack's Lootr rules");
            } else {
                containers.add(block);
            }
        }
        containers.sort(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()));
        require(containers.contains(Blocks.CHEST) && containers.contains(Blocks.BARREL), "Vanilla chest/barrel conversion missing");
        List<Identifier> tables = server.getLootManager().getIds(LootDataType.LOOT_TABLES).stream()
            .filter(id -> !ConfigManager.isBlacklisted(id))
            .filter(id -> server.getLootManager().getLootTable(id) != LootTable.EMPTY
                && server.getLootManager().getLootTable(id).getType() == LootContextTypes.CHEST)
            .sorted(Comparator.comparing(Identifier::toString)).toList();
        require(!tables.isEmpty(), "No chest loot tables loaded");

        Map<ServerWorld, Random> oldRandoms = new LinkedHashMap<>();
        Map<ServerWorld, CountingRandom> randoms = new LinkedHashMap<>();
        Map<ServerWorld, FixturePlayer> players = new LinkedHashMap<>();
        Map<String, int[]> coverage = new LinkedHashMap<>();
        int totalBoots = 0;
        try {
            ServerWorld first = worlds.get(0);
            Random old = first.random;
            CountingRandom exhaustive = new CountingRandom(400L, true);
            ((WorldRandomAccessor) first).gangsSmoke$setRandom(exhaustive);
            FixturePlayer oraclePlayer = new FixturePlayer(server, first, 0);
            int oracleBoots = 0;
            for (int i = 0; i < 400; i++) {
                oracleBoots += openFresh(first, i % 2 == 0 ? Blocks.CHEST : Blocks.BARREL, DUNGEON, oraclePlayer);
            }
            require(exhaustive.rolls == 400 && oracleBoots == 1,
                "Exhaustive 0..399 real-generation check failed: rolls=" + exhaustive.rolls + ", boots=" + oracleBoots);
            var parameters = new LootContextParameterSet.Builder(first)
                .add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                .add(LootContextParameters.THIS_ENTITY, oraclePlayer).build(LootContextTypes.CHEST);
            SimpleInventory shared = new SimpleInventory(27);
            server.getLootManager().getLootTable(DUNGEON).supplyInventory(shared, parameters, 400L);
            require(countBoots(shared) == 0 && exhaustive.rolls == 400, "Shared/non-Lootr fill awarded boots");
            ((WorldRandomAccessor) first).gangsSmoke$setRandom(old);

            for (int i = 0; i < worlds.size(); i++) {
                ServerWorld world = worlds.get(i);
                oldRandoms.put(world, world.random);
                CountingRandom random = new CountingRandom(0x4005000L + i, false);
                randoms.put(world, random);
                ((WorldRandomAccessor) world).gangsSmoke$setRandom(random);
                players.put(world, new FixturePlayer(server, world, i + 1));
            }
            for (int i = 0; i < 5000; i++) {
                ServerWorld world = worlds.get(i % worlds.size());
                Block container = containers.get((i / worlds.size()) % containers.size());
                Identifier table = tables.get(i % tables.size());
                int boots = openFresh(world, container, table, players.get(world));
                totalBoots += boots;
                String key = world.getRegistryKey().getValue() + "|" + Registries.BLOCK.getId(container) + "|" + table.getNamespace();
                int[] counts = coverage.computeIfAbsent(key, ignored -> new int[2]);
                counts[0]++;
                counts[1] += boots;
                if ((i + 1) % 500 == 0) System.out.println("LOCAL_BOOTS_OPENINGS " + (i + 1));
            }
            int rolls = randoms.values().stream().mapToInt(random -> random.rolls).sum();
            require(rolls == 5000, "Not exactly one 400-way roll per fresh personal container: " + rolls);
            // Wide fixed-seed regression guard; a 1/40 implementation is far outside this interval.
            require(totalBoots >= 2 && totalBoots <= 30, "5000-opening result inconsistent with 1/400: " + totalBoots);
        } finally {
            oldRandoms.forEach((world, random) -> ((WorldRandomAccessor) world).gangsSmoke$setRandom(random));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "BOOTS_PASS");
        report.put("harnessExclusions", Map.of(
            "worldedit", "User-approved local exclusion: expensive startup block-state enumeration; no container/loot/dimension content",
            "discord", "External integration excluded from isolated local testing"));
        report.put("loadedMods", FabricLoader.getInstance().getAllMods().stream()
            .map(mod -> mod.getMetadata().getId() + "@" + mod.getMetadata().getVersion()).sorted().toList());
        report.put("enabledDataPacks", server.getDataPackManager().getEnabledNames());
        report.put("dimensions", worlds.stream().map(world -> world.getRegistryKey().getValue().toString()).toList());
        report.put("eligibleSourceContainers", containers.stream().map(block -> Registries.BLOCK.getId(block).toString()).toList());
        report.put("ineligibleContainers", excluded);
        report.put("chestLootTables", tables.stream().map(Identifier::toString).toList());
        report.put("exhaustiveCheck", "400 first personal generations, every 400-way outcome once: exactly 1 boot; shared fill: 0");
        report.put("randomSample", Map.of("freshOpenings", 5000, "reopenings", 5000, "boots", totalBoots,
            "expectedBoots", 12.5, "observedRate", totalBoots / 5000.0, "seed", "0x4005000 + sorted dimension index"));
        report.put("coverage", coverage);
        System.out.println("BOOTS_SAMPLE_VERIFIED " + report.get("randomSample"));
        Files.writeString(Path.of("boots-results.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        report.put("trialChecks", verifyTrials(server.getOverworld()));
        report.put("status", "PASS");
        return report;
    }

    private static int openFresh(ServerWorld world, Block source, Identifier table, FixturePlayer player) {
        BlockPos pos = new BlockPos(0, Math.min(200, world.getTopY() - 10), 0);
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(pos.up(), Blocks.AIR.getDefaultState(), 2);
        BlockState original = source.getDefaultState();
        BlockState replacement = ConfigManager.replacement(original);
        BlockState state = replacement != null ? replacement : original;
        require(world.setBlockState(pos, state, 2), "Cannot place " + Registries.BLOCK.getId(source));
        BlockEntity blockEntity = world.getBlockEntity(pos);
        require(blockEntity instanceof LootableContainerBlockEntity && blockEntity instanceof ILootBlockEntity,
            "Expected real Lootr entity after conversion: " + source);
        ((LootableContainerBlockEntity) blockEntity).setLootTable(table, 400L);
        player.opened = null;
        player.deliveredBoots = 0;
        player.lastMessage = "";
        require(!player.isSpectator(), "Fixture player cannot open loot as a spectator");
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        state.onUse(world, player, Hand.MAIN_HAND, hit);
        require(player.opened != null, "Container failed to open: " + Registries.BLOCK.getId(source) + ", " + table
            + ", message=" + player.lastMessage);
        Inventory inventory = player.opened;
        int boots = countBoots(inventory) + player.deliveredBoots;
        require(boots <= 1, "More than one boot reward in one generation");
        CountingRandom random = (CountingRandom) world.random;
        int rolls = random.rolls;
        state.onUse(world, player, Hand.MAIN_HAND, hit);
        require(player.opened == inventory && random.rolls == rolls && countBoots(inventory) + player.deliveredBoots == boots,
            "Reopening rerolled personal loot");
        return boots;
    }

    private static List<String> verifyTrials(ServerWorld world)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        List<String> checks = new ArrayList<>();
        var registry = world.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);
        Identifier rootId = new Identifier("gangs_boots_trials_smoke", "boundary");
        StructureTemplate root = world.getStructureTemplateManager().getTemplateOrBlank(rootId);
        StructureTemplate connector = world.getStructureTemplateManager().getTemplateOrBlank(
            new Identifier("gangs_boots_trials_smoke", "boundary_connector"));
        for (String group : List.of("all", "melee", "ranged", "slow_ranged", "small_melee", "breeze")) {
            var active = registry.get(new Identifier("minecraft", "trial_chambers/spawner/" + group));
            require(active != null && active.getFallback().matchesId(
                new Identifier("gangstrials", "spawner/fallback/" + group)), "Paxi spawner override not active: " + group);
            NbtCompound tag = StringNbtReader.parse("{size:[1,1,1],palette:[{Name:\"minecraft:jigsaw\","
                + "Properties:{orientation:\"north_up\"}}],blocks:[{pos:[0,0,0],state:0,nbt:{"
                + "id:\"minecraft:jigsaw\",name:\"minecraft:empty\",target:\"minecraft:boundary_connector\","
                + "pool:\"gangs_boots_trials_smoke:boundary\",joint:\"aligned\",final_state:\"minecraft:air\"}}],entities:[]}");
            root.readNbt(Registries.BLOCK.getReadOnlyWrapper(), tag);
            connector.readNbt(Registries.BLOCK.getReadOnlyWrapper(), StringNbtReader.parse(
                "{size:[5,5,5],palette:[{Name:\"minecraft:jigsaw\",Properties:{orientation:\"south_up\"}},"
                + "{Name:\"minecraft:jigsaw\",Properties:{orientation:\"north_up\"}}],blocks:["
                + "{pos:[2,2,4],state:0,nbt:{id:\"minecraft:jigsaw\",name:\"minecraft:boundary_connector\","
                + "target:\"minecraft:empty\",pool:\"minecraft:empty\",joint:\"aligned\",final_state:\"minecraft:air\"}},"
                + "{pos:[2,2,2],state:1,nbt:{id:\"minecraft:jigsaw\",name:\"minecraft:empty\","
                + "target:\"minecraft:spawner_connector\",pool:\"minecraft:trial_chambers/spawner/" + group
                + "\",joint:\"aligned\",final_state:\"minecraft:air\"}}],entities:[]}"));
            var pool = new StructurePool(registry.getEntry(net.minecraft.structure.pool.StructurePools.EMPTY).orElseThrow(),
                List.of(Pair.of(StructurePoolElement.ofSingle(rootId.toString()).apply(StructurePool.Projection.RIGID), 1)));
            BlockPos origin = new BlockPos(128 + checks.size() * 16, 160, 128);
            placeStructure(world, RegistryEntry.of(pool), 1, origin, 400L);
            int spawners = 0;
            for (BlockPos pos : BlockPos.iterate(origin.add(-5, -5, -5), origin.add(5, 5, 5))) {
                if (world.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity spawner) {
                    require(spawner.getTrialSpawner().getData().hasMobToSpawn(spawner.getTrialSpawner(), world.random),
                        "Generated spawner has no configured mob: " + group);
                    NbtCompound saved = spawner.createNbt();
                    var restored = new TrialSpawnerBlockEntity(pos.toImmutable(), world.getBlockState(pos));
                    restored.readNbt(saved);
                    require(restored.getTrialSpawner().getData().hasMobToSpawn(restored.getTrialSpawner(), world.random),
                        "Save/reload lost spawner mob: " + group);
                    spawners++;
                }
            }
            require(spawners == 1, "Missing/duplicate spawner at depth limit: " + group + ", count=" + spawners);
            checks.add("Terminal fallback at maximum jigsaw depth places one configured, reloadable " + group + " spawner");
        }
        var chamber = registry.getEntry(net.minecraft.registry.RegistryKey.of(RegistryKeys.TEMPLATE_POOL,
            new Identifier("minecraft", "trial_chambers/chamber/end"))).orElseThrow();
        for (int i = 0; i < 3; i++) {
            expectedSpawners.clear();
            collectExpectedSpawners = true;
            try {
                placeStructure(world, chamber, 7, new BlockPos(1024 * (i + 1), 100, 1024), 400L + i);
            } finally {
                collectExpectedSpawners = false;
            }
            require(!expectedSpawners.isEmpty(), "Full chamber test found no intended spawner positions");
            List<BlockPos> missing = expectedSpawners.stream()
                .filter(pos -> !(world.getBlockEntity(pos) instanceof TrialSpawnerBlockEntity)).toList();
            require(missing.isEmpty(), "Full chamber still has missing intended spawners: " + missing);
            checks.add("Full generated chamber seed " + (400 + i) + ": all " + expectedSpawners.size() + " intended spawner positions filled");
        }
        int chunkX = 0;
        while (ChunkRandom.getSlimeRandom(chunkX, 0, world.getSeed(), 987234911L).nextInt(10) == 0) chunkX++;
        BlockPos slimePos = new BlockPos(new ChunkPos(chunkX, 0).getStartX() + 8, 160, 8);
        for (BlockPos floor : BlockPos.iterate(slimePos.add(-4, -1, -4), slimePos.add(4, -1, 4))) {
            world.setBlockState(floor, Blocks.STONE.getDefaultState(), 2);
        }
        require(!SlimeEntity.canSpawn(EntityType.SLIME, world, SpawnReason.SPAWNER, slimePos, Random.create(400)),
            "Control fixture unexpectedly permits natural slime spawning");
        TrialSpawnerFixes.beginTrialSpawn();
        try {
            require(SlimeEntity.canSpawn(EntityType.SLIME, world, SpawnReason.SPAWNER, slimePos, Random.create(400)),
                "Trial slime predicate still checks natural chunk/height restrictions");
            require(!SlimeEntity.canSpawn(EntityType.SLIME, world, SpawnReason.NATURAL, slimePos, Random.create(400)),
                "Natural slime restrictions changed");
        } finally {
            TrialSpawnerFixes.endTrialSpawn();
        }
        var template = world.getStructureTemplateManager().getTemplate(new Identifier("gangstrials", "spawner/small_melee/slime")).orElseThrow();
        BlockPos templatePos = slimePos.add(-1, -1, -1);
        require(template.place(world, templatePos, templatePos, new net.minecraft.structure.StructurePlacementData(), world.random, 2),
            "Slime template placement failed");
        var slimeSpawner = (TrialSpawnerBlockEntity) world.getBlockEntity(slimePos);
        require(slimeSpawner != null, "Slime spawner missing");
        boolean spawned = false;
        for (int attempt = 0; attempt < 100 && !spawned; attempt++) {
            var uuid = slimeSpawner.getTrialSpawner().spawnMob(world, slimePos);
            if (uuid.isPresent()) {
                require(world.getEntity(uuid.get()) instanceof SlimeEntity, "Trial spawned wrong entity");
                world.getEntity(uuid.get()).discard();
                spawned = true;
            }
        }
        require(spawned && !TrialSpawnerFixes.ignoresLight(), "Actual slime trial spawn failed or leaked spawn scope");
        checks.add("Actual slime spawned in bright, above-height-limit non-slime chunk; natural spawns unchanged; scope cleared");
        return checks;
    }

    private static void placeStructure(ServerWorld world, RegistryEntry<StructurePool> pool, int depth,
            BlockPos origin, long seed) {
        var generator = world.getChunkManager().getChunkGenerator();
        var context = new Structure.Context(world.getRegistryManager(), generator, generator.getBiomeSource(),
            world.getChunkManager().getNoiseConfig(), world.getStructureTemplateManager(), seed,
            new ChunkPos(origin), world, biome -> true);
        var position = StructurePoolBasedGenerator.generate(context, pool, Optional.empty(), depth, origin,
            false, Optional.empty(), 128).orElseThrow(() -> new IllegalStateException("Structure generation failed"));
        var pieces = position.generate().toList().pieces();
        require(!pieces.isEmpty(), "No structure pieces generated");
        Random random = Random.create(seed);
        for (var piece : pieces) {
            require(piece instanceof PoolStructurePiece, "Unexpected non-pool structure piece");
            ((PoolStructurePiece) piece).generate(world, world.getStructureAccessor(), generator, random,
                BlockBox.infinite(), origin, false);
        }
    }

    private static int countBoots(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (Registries.ITEM.getId(stack.getItem()).equals(BOOTS)) count += stack.getCount();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class CountingRandom extends CheckedRandom {
        private final boolean exhaustive;
        int rolls;

        CountingRandom(long seed, boolean exhaustive) {
            super(seed);
            this.exhaustive = exhaustive;
        }

        @Override
        public int nextInt(int bound) {
            if (bound == 400) {
                int outcome = rolls++ % 400;
                return exhaustive ? outcome : super.nextInt(bound);
            }
            return super.nextInt(bound);
        }
    }

    private static final class FixturePlayer extends ServerPlayerEntity {
        Inventory opened;
        int deliveredBoots;
        String lastMessage = "";

        FixturePlayer(MinecraftServer server, ServerWorld world, int id) {
            super(server, world, new GameProfile(new UUID(400L, id), "BootsFixture"));
        }

        @Override
        public OptionalInt openHandledScreen(NamedScreenHandlerFactory factory) {
            require(factory instanceof Inventory, "Unexpected non-inventory Lootr menu provider");
            opened = (Inventory) factory;
            return OptionalInt.of(1);
        }

        @Override
        public boolean giveItemStack(ItemStack stack) {
            if (Registries.ITEM.getId(stack.getItem()).equals(BOOTS)) deliveredBoots += stack.getCount();
            return true;
        }

        @Override
        public void sendMessage(Text message, boolean overlay) {
            lastMessage = message.getString();
        }
    }
}
