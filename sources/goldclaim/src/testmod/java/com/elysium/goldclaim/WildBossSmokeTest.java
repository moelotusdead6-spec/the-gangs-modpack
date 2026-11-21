package com.elysium.goldclaim;

import com.github.alexmodguy.alexscaves.server.entity.ai.UnderzealotSacrificeGoal;
import com.github.alexmodguy.alexscaves.server.entity.living.UnderzealotEntity;
import com.mojang.authlib.GameProfile;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.Placeholders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** GOLDCLAIM_WILD_BOSS_SMOKE=true: /wild Alex's Caves boss refusal, kit countdown placeholder and staff Cosmetic Keys. */
final class WildBossSmokeTest {
    private static int stage;
    private static ServerPlayerEntity player;
    private static BlockPos core;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (stage < 0 || server.getTicks() < 40) return;
            try {
                if (stage == 0) {
                    start(server);
                    stage = server.getTicks();
                } else if (server.getTicks() >= stage + 40) {
                    finish(server);
                    stage = -1;
                }
            } catch (Exception failure) {
                stage = -1;
                throw new IllegalStateException("Wild boss smoke failed", failure);
            }
        });
    }

    private static void start(MinecraftServer server) throws Exception {
        ServerWorld wild = server.getOverworld();
        ServerWorld nether = server.getWorld(World.NETHER);
        for (String boss : new String[]{"luxtructosaurus", "forsaken", "hullbreaker", "magnetron"}) {
            EntityType<?> type = type(boss);
            require(!wild.spawnEntity(type.create(wild)), boss + " must be refused in /wild");
            Entity allowed = type.create(nether);
            allowed.refreshPositionAndAngles(0, 80, 0, 0, 0);
            require(nether.spawnEntity(allowed), boss + " must still spawn outside /wild");
            allowed.discard();
        }
        Entity raptor = type("vallumraptor").create(wild);
        require(AlexCavesProtection.blocksWildGenerationSpawn(raptor, wild), "Generation spawns of hostile dinosaurs must be refused in /wild");
        require(!AlexCavesProtection.blocksWildGenerationSpawn(type("vallumraptor").create(nether), nether), "Dinosaurs stay outside /wild");
        require(!AlexCavesProtection.blocksWildGenerationSpawn(type("grottoceratops").create(wild), wild), "Passive dinosaurs stay");
        require(AlexCavesProtection.blocksWildGenerationSpawn(type("deep_one").create(wild), wild), "AC monsters refused at generation");

        UnderzealotEntity zealot = (UnderzealotEntity)type("underzealot").create(wild);
        require(!new UnderzealotSacrificeGoal(zealot).canStart(), "Forsaken ritual must not start in /wild");

        BlockPos spawn = wild.getSpawnPos();
        player = new ServerPlayerEntity(server, wild, new GameProfile(UUID.randomUUID(), "WildBossFixture"));
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
        player.refreshPositionAndAngles(spawn.getX() + 4.5, spawn.getY() + 1, spawn.getZ() + 0.5, 0, 0);
        wild.onPlayerConnected(player);

        SpawnEggItem egg = SpawnEggItem.forEntity(type("luxtructosaurus"));
        require(egg != null, "Luxtructosaurus spawn egg missing");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(egg));
        require(egg.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(spawn), Direction.UP, spawn.down(), false))) == ActionResult.FAIL,
            "Boss egg on block must fail in /wild");
        require(!egg.use(wild, player, Hand.MAIN_HAND).getResult().isAccepted(), "Boss egg use must fail in /wild");
        require(player.getMainHandStack().getCount() == 1, "Boss egg must not be consumed");

        core = spawn.up(3);
        wild.setBlockState(core, Registries.BLOCK.get(new Identifier("alexscaves", "volcanic_core")).getDefaultState());
        require(wild.getBlockEntity(core) != null, "Volcanic core block entity missing");
        ItemEntity catalyst = new ItemEntity(wild, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5,
            new ItemStack(Registries.ITEM.get(new Identifier("alexscaves", "ominous_catalyst"))));
        catalyst.setOwner(player.getUuid());
        catalyst.setNoGravity(true);
        require(wild.spawnEntity(catalyst), "Catalyst item entity could not spawn");

        String countdown = Placeholders.parseText(Text.literal("%goldclaim:kit_ready w2%"),
            PlaceholderContext.of(player)).getString();
        require(countdown.equals("Ready in 7d 0h"), "Unexpected kit countdown: " + countdown);
        require(Placeholders.parseText(Text.literal("%goldclaim:kit_ready w1%"), PlaceholderContext.of(player))
            .getString().equals("Ready to claim!"), "Week 1 must be ready on a fresh cycle");

        Class<?> tokens = Class.forName("com.thegangs.gangshats.CosmeticTokenState");
        Object state = tokens.getMethod("get", MinecraftServer.class).invoke(null, server);
        var consumeKey = tokens.getMethod("consumeKey", ItemStack.class);
        ItemStack plainKey = new ItemStack(Registries.ITEM.get(new Identifier("gangshats", "cosmetic_key")));
        require(!plainKey.isEmpty(), "Cosmetic key item missing");
        require((Boolean)consumeKey.invoke(state, plainKey), "/give Cosmetic Key must be redeemable");
        ItemStack forged = plainKey.copy();
        forged.getOrCreateNbt().putUuid("gangscosmetics_token_id", UUID.randomUUID());
        forged.getOrCreateNbt().putString("gangscosmetics_token_kind", "key");
        require(!(Boolean)consumeKey.invoke(state, forged), "Unknown tokenized keys must stay rejected");
    }

    private static void finish(MinecraftServer server) throws Exception {
        ServerWorld wild = server.getOverworld();
        Box around = new Box(core).expand(32);
        var left = wild.getEntitiesByClass(ItemEntity.class, around, item -> item.isAlive()
            && Registries.ITEM.getId(item.getStack().getItem()).toString().equals("alexscaves:ominous_catalyst"));
        require(left.isEmpty(), "Catalyst must be handed back, still on the ground: " + left.size());
        int held = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (Registries.ITEM.getId(stack.getItem()).toString().equals("alexscaves:ominous_catalyst")) held += stack.getCount();
        }
        require(held == 1, "Catalyst must be returned to the thrower, found " + held);
        require(wild.getEntitiesByType(type("luxtructosaurus"), around, Entity::isAlive).isEmpty(), "Volcanic core summoned a boss in /wild");
        Files.writeString(Path.of("policy-smoke-passed.txt"), "PASS: /wild AC boss refusal (spawnEntity, eggs, volcanic core, "
            + "Forsaken ritual, generation), kit countdown placeholder, staff Cosmetic Keys.\n");
        server.stop(false);
    }

    private static EntityType<?> type(String path) {
        Identifier id = new Identifier("alexscaves", path);
        require(Registries.ENTITY_TYPE.containsId(id), "Missing entity " + id);
        return Registries.ENTITY_TYPE.get(id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
