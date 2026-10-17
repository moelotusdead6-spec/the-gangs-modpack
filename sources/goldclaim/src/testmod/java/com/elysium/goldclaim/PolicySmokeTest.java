package com.elysium.goldclaim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

public final class PolicySmokeTest implements ModInitializer {
    @Override
    public void onInitialize() {
        if ("true".equals(System.getenv("GOLDCLAIM_CITY_LOOT_SMOKE"))) {
            EndCityLootSmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_BIOME_SMOKE"))) {
            RswEndBiomeSmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_KIT_SMOKE"))) {
            KitRewardSmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_CITY_SMOKE"))) {
            EndCitySmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_END_LOOT_SMOKE"))) {
            BetterEndLootSmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_GATEWAY_SMOKE"))) {
            EndGatewaySmokeTest.register();
            return;
        }
        if ("true".equals(System.getenv("GOLDCLAIM_RSW_SMOKE"))) {
            RswDragonSmokeTest.register();
            return;
        }
        EntityType<PathAwareEntity> moddedHostile = Registry.register(Registries.ENTITY_TYPE,
            new Identifier("goldclaim_policy_smoke:modded_hostile"),
            EntityType.Builder.<PathAwareEntity>create((type, world) -> new PathAwareEntity(type, world) {},
                SpawnGroup.MONSTER).setDimensions(0.6f, 1.8f).build("goldclaim_policy_smoke:modded_hostile"));
        FabricDefaultAttributeRegistry.register(moddedHostile, MobEntity.createMobAttributes());
        EntityType<ZombieEntity> moddedMonster = Registry.register(Registries.ENTITY_TYPE,
            new Identifier("goldclaim_policy_smoke:modded_monster"),
            EntityType.Builder.<ZombieEntity>create(ZombieEntity::new, SpawnGroup.CREATURE)
                .setDimensions(0.6f, 1.8f).build("goldclaim_policy_smoke:modded_monster"));
        FabricDefaultAttributeRegistry.register(moddedMonster, ZombieEntity.createZombieAttributes());
        EntityType<WolfEntity> moddedPet = Registry.register(Registries.ENTITY_TYPE,
            new Identifier("goldclaim_policy_smoke:modded_pet"),
            EntityType.Builder.<WolfEntity>create(WolfEntity::new, SpawnGroup.MONSTER)
                .setDimensions(0.6f, 0.85f).build("goldclaim_policy_smoke:modded_pet"));
        FabricDefaultAttributeRegistry.register(moddedPet, WolfEntity.createWolfAttributes());
        Item inception = Registry.register(Registries.ITEM, new Identifier(BannedContent.INCEPTION_UPGRADE),
            new Item(new Item.Settings()));
        Registry.register(Registries.ITEM, new Identifier("goldclaim_policy_smoke:other_plushie"),
            new Item(new Item.Settings()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verifyClaimCombat(server, moddedHostile, moddedMonster, moddedPet);
            var trash = server.getCommandManager().getDispatcher().getRoot().getChild("trash");
            require(trash != null && trash.getCommand() != null && trash.getChildren().isEmpty(),
                "public native /trash command must be registered without subcommands");
            require(new ItemStack(inception).isEmpty(), "new inception stacks must be empty");
            ItemStack refilled = new ItemStack(inception);
            refilled.setCount(64);
            require(refilled.isEmpty(), "inception stacks cannot be refilled");
            NbtCompound installed = stack(BannedContent.INCEPTION_UPGRADE);
            require(ItemStack.fromNbt(installed).isEmpty(), "saved/installed inception must load empty");
            NbtList contents = new NbtList();
            contents.add(stack(BannedContent.INCEPTION_UPGRADE));
            contents.add(stack("minecraft:diamond"));
            NbtCompound tag = new NbtCompound();
            tag.put("Items", contents);
            ItemStack container = new ItemStack(Items.SHULKER_BOX);
            container.setNbt(tag);
            require(contents.size() == 1 && contents.getCompound(0).getString("id").equals("minecraft:diamond"),
                "nested storage must retain unrelated contents");
            try {
                new ItemStackArgument(Registries.ITEM.getEntry(inception), null).createStack(1, false);
                throw new IllegalStateException("banned item command was not rejected");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                require(expected.getMessage().contains(BannedContent.INCEPTION_UPGRADE),
                    "command rejection must identify the banned item");
            }
            require(server.getRecipeManager().get(new Identifier("goldclaim_policy_smoke:inception")).isEmpty(),
                "inception recipe must be removed");
            require(server.getRecipeManager().get(new Identifier("goldclaim_policy_smoke:other_plushie")).isEmpty(),
                "other plushie recipe must be removed");
            require(server.getRecipeManager().get(new Identifier("sarosplayerplushiemod:plushie")).isPresent(),
                "actual Saro plushie recipe must remain");
            require(!new ItemStack(Items.DIAMOND).isEmpty(), "unrelated items must remain usable");
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: native /trash registration, runtime item creation, refill, NBT load, nested storage, "
                        + "commands, recipe removal, actual Saro recipe, untrusted hostile combat, "
                        + "protected animals/pets/decorations, trusted combat, entity use, inventory and block protection.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record smoke-test result", failure);
            }
            server.stop(false);
        });
    }

    private static void verifyClaimCombat(MinecraftServer server, EntityType<?> moddedHostile,
            EntityType<?> moddedMonster, EntityType<?> moddedPet) {
        ClaimManager claims;
        try {
            var field = GoldClaimMod.class.getDeclaredField("claimManager");
            field.setAccessible(true);
            claims = (ClaimManager)field.get(GoldClaimMod.getInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access claim manager for combat fixture", failure);
        }
        ServerWorld world = server.getOverworld();
        ServerPlayerEntity owner = testPlayer(server, world, "ClaimOwner");
        ServerPlayerEntity visitor = testPlayer(server, world, "ClaimVisitor");
        String dimension = world.getRegistryKey().getValue().toString();
        BlockPos pos = new BlockPos(100, 64, 100);
        claims.removeClaimAt(owner.getUuid(), dimension, pos.getX(), pos.getZ(), true, true);
        require(claims.createClaim(owner.getUuid(), owner.getGameProfile().getName(),
            dimension, 100, 110, 100, 110).success(), "combat fixture claim must be created");
        require(!claims.canModify(visitor.getUuid(), visitor.getGameProfile().getName(),
            false, dimension, 100, 100), "visitor must not have claim trust");
        for (EntityType<?> type : new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON,
                EntityType.CREEPER, EntityType.SLIME, EntityType.MAGMA_CUBE, EntityType.GHAST,
                EntityType.PHANTOM, EntityType.WITHER, EntityType.ENDER_DRAGON, moddedHostile, moddedMonster}) {
            Entity target = type.create(world);
            require(target != null, "hostile fixture entity must exist: " + type);
            target.setPosition(100.5, 64, 100.5);
            require(AttackEntityCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
                target, null) == ActionResult.PASS, "untrusted visitors must be able to attack " + type);
            require(UseEntityCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
                target, null) == ActionResult.FAIL, "hostile combat must not grant entity-use access");
        }
        for (EntityType<?> type : new EntityType<?>[]{EntityType.COW, EntityType.VILLAGER,
                EntityType.WOLF, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, moddedPet}) {
            Entity target = type.create(world);
            require(target != null, "protected fixture entity must exist: " + type);
            target.setPosition(100.5, 64, 100.5);
            if (target instanceof net.minecraft.entity.passive.TameableEntity pet) {
                pet.setOwnerUuid(owner.getUuid());
                pet.setTamed(true);
            }
            require(AttackEntityCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
                target, null) == ActionResult.FAIL, "untrusted visitors must not attack " + type);
            require(AttackEntityCallback.EVENT.invoker().interact(owner, world, Hand.MAIN_HAND,
                target, null) == ActionResult.PASS, "owners must retain entity attack access");
            var claim = claims.getClaimAt(dimension, 100, 100).orElseThrow();
            claim.addTrusted(visitor.getUuid());
            require(AttackEntityCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
                target, null) == ActionResult.PASS, "trusted visitors must retain entity attack access");
            claim.removeTrusted(visitor.getUuid());
            target.setPosition(120.5, 64, 120.5);
            require(AttackEntityCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
                target, null) == ActionResult.PASS, "unclaimed entity attacks must remain allowed");
        }
        require(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, visitor, pos,
            Blocks.STONE.getDefaultState(), null), "hostile combat must not grant block-breaking access");
        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
        require(UseBlockCallback.EVENT.invoker().interact(visitor, world, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)) == ActionResult.FAIL,
            "hostile combat must not grant chest inventory access");
        claims.removeClaimAt(owner.getUuid(), dimension, 100, 100, true, true);
    }

    private static ServerPlayerEntity testPlayer(MinecraftServer server, ServerWorld world, String name) {
        return new ServerPlayerEntity(server, world,
            new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name)) {
            @Override
            public void sendMessage(Text message, boolean overlay) {
                require(!message.getString().isBlank(), "claim denial must explain the protection");
            }
        };
    }

    private static NbtCompound stack(String id) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id);
        nbt.putByte("Count", (byte)1);
        return nbt;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
