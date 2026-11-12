package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

final class HubFurnitureSmokeTest {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                verifyHub(server);
                verifyFurniture(server);
                verifyShop();
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: hub projectile spawn/tick/passenger and item-use guards; non-hub behavior retained; "
                    + "all four furniture mods loaded and every registered item absent from shop; "
                    + "native furniture config defaults retained and local/online crafting restricted to level 2.\n");
                server.stop(false);
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Hub/furniture smoke failed", failure);
            }
        });
    }

    private static void verifyHub(MinecraftServer server) throws ReflectiveOperationException {
        var field = GoldClaimMod.class.getDeclaredField("config");
        field.setAccessible(true);
        var config = (GoldClaimConfig)field.get(GoldClaimMod.getInstance());
        String originalHub = config.portalHubDimension;
        ServerWorld world = server.getOverworld();
        var player = new FixturePlayer(server, world, false);
        var projectileTypes = new ArrayList<EntityType<?>>(List.of(EntityType.ARROW, EntityType.SPECTRAL_ARROW, EntityType.TRIDENT,
            EntityType.SNOWBALL, EntityType.EGG, EntityType.ENDER_PEARL, EntityType.EXPERIENCE_BOTTLE,
            EntityType.POTION, EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.DRAGON_FIREBALL,
            EntityType.WITHER_SKULL, EntityType.SHULKER_BULLET, EntityType.FIREWORK_ROCKET, EntityType.EYE_OF_ENDER));
        for (String id : List.of("alexscaves:tephra", "alexscaves:water_bolt", "alexscaves:wave",
                "alexscaves:desolate_dagger", "alexscaves:dinosaur_spirit", "alexscaves:spinning_peppermint",
                "alexscaves:sugar_staff_hex", "alexscaves:thrown_waste_drum", "alexscaves:magnetic_weapon",
                "supplementaries:bomb", "supplementaries:cannonball")) {
            require(Registries.ENTITY_TYPE.containsId(new Identifier(id)), "Missing modded projectile fixture: " + id);
            projectileTypes.add(Registries.ENTITY_TYPE.get(new Identifier(id)));
        }
        try {
            config.portalHubDimension = world.getRegistryKey().getValue().toString();
            require(GoldClaimMod.isHubWorld(world), "Configured hub not recognized");
            for (var type : projectileTypes) {
                var entity = type.create(world);
                require(HubProjectilePolicy.isProjectile(entity), "Fixture is not recognized as a projectile: " + type);
                entity.setPosition(0.5, 80, 0.5);
                require(!world.spawnEntity(entity) && entity.isRemoved(), "Hub spawn permitted: " + type);
                var existing = type.create(world);
                world.tickEntity(existing);
                require(existing.isRemoved(), "Existing hub projectile ticked: " + type);
            }
            var vehicle = EntityType.BOAT.create(world);
            var passenger = EntityType.ARROW.create(world);
            passenger.startRiding(vehicle, true);
            var tickPassenger = java.util.Arrays.stream(ServerWorld.class.getDeclaredMethods())
                .filter(method -> java.util.Arrays.equals(method.getParameterTypes(),
                    new Class<?>[]{net.minecraft.entity.Entity.class, net.minecraft.entity.Entity.class}))
                .findFirst().orElseThrow();
            tickPassenger.setAccessible(true);
            tickPassenger.invoke(world, vehicle, passenger);
            require(passenger.isRemoved(), "Hub projectile passenger ticked");
            var item = new ItemEntity(world, 0.5, 80, 0.5, new ItemStack(Items.DIAMOND));
            require(world.spawnEntity(item), "Hub item drops blocked");
            item.discard();
            var stand = EntityType.ARMOR_STAND.create(world);
            stand.setPosition(0.5, 80, 0.5);
            require(world.spawnEntity(stand), "Hub display entities blocked");
            stand.discard();
            var launchItems = List.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.SNOWBALL, Items.EGG,
                Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.SPLASH_POTION, Items.LINGERING_POTION,
                Items.FIREWORK_ROCKET, Items.ENDER_EYE, Items.FISHING_ROD);
            for (var itemType : launchItems) {
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(itemType, 1));
                require(UseItemCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND).getResult()
                    == ActionResult.FAIL, "Hub item use permitted: " + itemType);
                require(player.getMainHandStack().getCount() == 1, "Denied hub use consumed item");
            }
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.APPLE));
            require(UseItemCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND).getResult()
                == ActionResult.PASS, "Non-projectile item use blocked");
            config.portalHubDimension = "gangs:hub";
            require(!GoldClaimMod.isHubWorld(world), "Wild confused with hub");
            for (var type : projectileTypes) {
                var entity = type.create(world);
                entity.setPosition(0.5, 80, 0.5);
                require(world.spawnEntity(entity) && !entity.isRemoved(), "Non-hub spawn blocked: " + type);
                entity.discard();
            }
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SNOWBALL));
            require(UseItemCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND).getResult()
                == ActionResult.PASS, "Non-hub projectile item use blocked");
            System.out.println("PASS: " + projectileTypes.size() + " vanilla/modded projectile types blocked only in configured hub.");
        } finally {
            config.portalHubDimension = originalHub;
        }
    }

    private static void verifyFurniture(MinecraftServer server) throws ReflectiveOperationException {
        var configType = Class.forName("net.conczin.immersive_furniture.config.Config");
        Object config = configType.getMethod("getInstance").invoke(null);
        Object defaults = configType.getConstructor(String.class).newInstance("immersive_furniture");
        require(configType.getField("requiredCraftingPermissionLevel").getInt(config) == 2,
            "Native crafting permission restriction not loaded");
        for (var field : configType.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || Set.of("version", "requiredCraftingPermissionLevel").contains(field.getName())) continue;
            require(java.util.Objects.equals(field.get(defaults), field.get(config)),
                "Unrelated furniture default changed: " + field.getName());
        }
        var dataType = Class.forName("net.conczin.immersive_furniture.data.FurnitureData");
        var requestType = Class.forName("net.conczin.immersive_furniture.network.c2s.CraftRequest");
        var handle = requestType.getMethod("handle", net.minecraft.entity.player.PlayerEntity.class);
        var ordinary = new FixturePlayer(server, server.getOverworld(), false);
        var staff = new FixturePlayer(server, server.getOverworld(), true);
        var furnitureItems = Class.forName("net.conczin.immersive_furniture.item.Items");
        var material = (net.minecraft.item.Item)furnitureItems.getField("CRAFTING_MATERIAL").get(null);
        var furniture = (net.minecraft.item.Item)furnitureItems.getField("FURNITURE").get(null);
        for (int contentId : new int[]{0, 12345}) {
            Object data = dataType.getConstructor().newInstance();
            dataType.getField("contentid").setInt(data, contentId);
            Object request = requestType.getConstructor(dataType, boolean.class).newInstance(data, false);
            for (boolean creative : new boolean[]{false, true}) {
                ordinary.getAbilities().creativeMode = creative;
                staff.getAbilities().creativeMode = creative;
                ordinary.getInventory().clear();
                ordinary.getInventory().setStack(0, new ItemStack(material, 64));
                ordinary.messages.clear();
                handle.invoke(request, ordinary);
                require(ordinary.getInventory().count(furniture) == 0, "Non-staff crafted furniture");
                require(ordinary.getInventory().count(material) == 64, "Denied crafting consumed materials");
                require(ordinary.messages.stream().anyMatch(message -> message.contains("permission")),
                    "Non-staff denial notice missing");
                staff.getInventory().clear();
                staff.getInventory().setStack(0, new ItemStack(material, 64));
                handle.invoke(request, staff);
                require(staff.getInventory().count(furniture) == 1, "Staff furniture crafting failed");
            }
        }
    }

    private static void verifyShop() throws ReflectiveOperationException {
        Object catalog = Class.forName("com.gangs.gangshop.GangShopMod").getField("CATALOG").get(null);
        var entry = catalog.getClass().getMethod("getEntry", Identifier.class);
        var counts = new HashMap<String, Integer>();
        Set<String> namespaces = Set.of("bf_blockpack", "immersive_furniture", "urban_decor", "justoutdoorstuffs");
        for (var item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!namespaces.contains(id.getNamespace())) continue;
            counts.merge(id.getNamespace(), 1, Integer::sum);
            require(entry.invoke(catalog, id) == null, "Furniture leaked into shop: " + id);
        }
        require(counts.keySet().equals(namespaces), "Missing furniture mod items: " + counts);
        require(entry.invoke(catalog, new Identifier("minecraft:cobblestone")) != null,
            "Existing vanilla shop behavior changed");
        System.out.println("PASS: furniture item counts excluded from shop: " + counts);
    }

    private static final class FixturePlayer extends ServerPlayerEntity {
        private final boolean staff;
        private final List<String> messages = new ArrayList<>();

        FixturePlayer(MinecraftServer server, ServerWorld world, boolean staff) {
            super(server, world, new GameProfile(UUID.randomUUID(), staff ? "StaffFixture" : "PlayerFixture"));
            this.staff = staff;
        }

        @Override public boolean hasPermissionLevel(int level) { return level <= (staff ? 2 : 0); }
        @Override public void sendMessage(Text message, boolean overlay) { messages.add(message.getString()); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
