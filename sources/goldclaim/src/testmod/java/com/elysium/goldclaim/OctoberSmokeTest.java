package com.elysium.goldclaim;

import com.brutalbosses.entity.capability.BossCapEntity;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

final class OctoberSmokeTest {
    private static final List<String> messages = new ArrayList<>();
    private static int ticks;
    private static net.minecraft.item.Item teleportItem;

    static void register() {
        teleportItem = net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.ITEM,
            new Identifier("goldclaim_policy_smoke:local_teleport"),
            new net.minecraft.item.Item(new net.minecraft.item.Item.Settings()) {
                @Override
                public net.minecraft.util.TypedActionResult<ItemStack> use(net.minecraft.world.World world,
                        net.minecraft.entity.player.PlayerEntity user, Hand hand) {
                    var player = (ServerPlayerEntity)user;
                    player.teleport(player.getServerWorld(), 50.5, 100, 50.5, 0, 0);
                    return net.minecraft.util.TypedActionResult.success(player.getStackInHand(hand));
                }
            });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            require(++ticks < 1200, "October smoke timed out");
            if (ticks < 10) return;
            var dimensions = List.of(GoldClaimMod.RSW_DIMENSION, GoldClaimMod.RSW_NETHER_DIMENSION,
                GoldClaimMod.RSW_THE_END_DIMENSION);
            for (String id : dimensions) if (world(server, id) == null) return;
            try {
                verifyReportPayloadLimit();
                verifyHub(server);
                verifyCleanup(server);
                verifySales(server);
                verifyShopExclusions();
                require(!RswRoamingBosses.eligible(EntityType.ZOMBIE.create(server.getOverworld()), SpawnReason.NATURAL),
                    "Wild natural mobs qualified for roaming bosses");
                for (String id : dimensions) verifyBosses(world(server, id));
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: bounded 64 KiB report decoding; hub server corrections without chat spam; "
                    + "hub pearls/chorus in both hands, pearl impact, random and mod local teleport blocking; "
                    + "command/cross-world travel and outside-hub use preserved; exact 10..1 countdown, "
                    + "67-item cleanup total; actual normal/custom-name purchases, inventory and balances; "
                    + "1% natural bosses in all three RSWs, real abilities and saved boss identity, "
                    + "non-natural/Wild exclusions.\n");
                server.stop(false);
                ticks = -100000;
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("October integration verification failed", failure);
            }
        });
    }

    private static void verifyReportPayloadLimit() {
        var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        try {
            buffer.writeIdentifier(new Identifier("fiw-mods-api", "response"));
            buffer.writeZero(65536);
            var packet = new net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket(buffer);
            try {
                require(packet.getData().readableBytes() == 65536, "Large report payload was truncated");
            } finally {
                packet.getData().release();
            }
        } finally {
            buffer.release();
        }
        buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        try {
            buffer.writeIdentifier(new Identifier("fiw-mods-api", "response"));
            buffer.writeZero(1048577);
            boolean rejected = false;
            try {
                new net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket(buffer);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "Report payloads above 1 MiB must remain rejected");
        } finally {
            buffer.release();
        }
    }

    private static void verifyHub(MinecraftServer server) throws ReflectiveOperationException {
        var world = server.getOverworld();
        var player = player(server, world, "HubFixture");
        var field = GoldClaimMod.class.getDeclaredField("config");
        field.setAccessible(true);
        var config = (GoldClaimConfig)field.get(GoldClaimMod.getInstance());
        String original = config.portalHubDimension;
        config.portalHubDimension = world.getRegistryKey().getValue().toString();
        try {
            player.refreshPositionAndAngles(0.5, 100, 0.5, 0, 0);
            for (var item : List.of(Items.ENDER_PEARL, Items.CHORUS_FRUIT)) {
                for (Hand hand : Hand.values()) {
                    player.setStackInHand(hand, new ItemStack(item, 4));
                    var result = UseItemCallback.EVENT.invoker().interact(player, world, hand);
                    require(result.getResult() == ActionResult.FAIL && player.getStackInHand(hand).getCount() == 4,
                        "Hub teleport item must be blocked without consumption in " + hand);
                }
            }
            Vec3d before = player.getPos();
            require(!player.teleport(10.5, 100, 10.5, false), "Hub random teleport succeeded");
            messages.clear();
            player.requestTeleport(20.5, 100, 20.5);
            require(player.getX() == 20.5, "Server position correction was blocked in the hub");
            player.requestTeleportAndDismount(30.5, 100, 30.5);
            require(player.getX() == 30.5, "Server dismount correction was blocked in the hub");
            player.requestTeleportOffset(10, 0, 10);
            require(player.getX() == 40.5, "Server offset correction was blocked in the hub");
            require(messages.isEmpty(), "Server corrections spammed hub teleport denial messages");
            before = player.getPos();
            var itemUser = player;
            HubTeleportPolicy.useItem(itemUser, () -> {
                itemUser.requestTeleport(20.5, 100, 20.5);
                itemUser.requestTeleportAndDismount(30.5, 100, 30.5);
                itemUser.requestTeleportOffset(10, 0, 10);
                return ActionResult.SUCCESS;
            });
            require(player.getPos().equals(before), "Item-triggered local teleport bypassed the hub policy");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(teleportItem));
            player.interactionManager.interactItem(player, world, player.getMainHandStack(), Hand.MAIN_HAND);
            require(player.getPos().equals(before), "Modded item bypassed the hub policy through direct teleport");
            var pearl = new net.minecraft.entity.projectile.thrown.EnderPearlEntity(world, player);
            var collision = pearl.getClass().getDeclaredMethod("onCollision", HitResult.class);
            collision.setAccessible(true);
            collision.invoke(pearl, net.minecraft.util.hit.BlockHitResult.createMissed(
                new Vec3d(40.5, 100, 40.5), net.minecraft.util.math.Direction.UP,
                new net.minecraft.util.math.BlockPos(40, 100, 40)));
            require(pearl.isRemoved() && player.getPos().equals(before), "Hub pearl impact moved the player");
            player.teleport(world, 2.5, 100, 2.5, 0, 0);
            require(player.getX() == 2.5, "Explicit administrative/command teleport was blocked");
            player.teleport(world(server, GoldClaimMod.RSW_DIMENSION), 0.5, 100, 0.5, 0, 0);
            require(!HubTeleportPolicy.deny(player), "Travel out of hub was blocked");
        } finally {
            config.portalHubDimension = original;
        }
        player = player(server, world, "OutsideHubFixture");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
        require(UseItemCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND).getResult() == ActionResult.PASS,
            "Pearls outside hub were blocked");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(teleportItem));
        player.interactionManager.interactItem(player, world, player.getMainHandStack(), Hand.MAIN_HAND);
        require(player.getX() == 50.5, "Modded teleport item outside hub was blocked");
    }

    private static void verifyShopExclusions() throws ReflectiveOperationException, java.io.IOException {
        Object prices = Class.forName("com.gangs.gangshop.GangShopMod").getField("PRICES").get(null);
        var denied = prices.getClass().getMethod("isDenied", Identifier.class);
        var config = com.google.gson.JsonParser.parseString(Files.readString(Path.of("config/gangshop/overrides.json")))
            .getAsJsonObject().getAsJsonObject("denyItems");
        require(config.size() == 93, "Shop fixture did not contain all preserved/shulker/bin exclusions");
        for (String id : config.keySet()) {
            require(Boolean.TRUE.equals(denied.invoke(prices, new Identifier(id))), "Shop did not exclude " + id);
        }
    }

    private static void verifyCleanup(MinecraftServer server) throws ReflectiveOperationException {
        var player = player(server, server.getOverworld(), "CleanupFixture");
        server.getPlayerManager().getPlayerList().add(player);
        var schedule = GroundCleanup.class.getDeclaredField("schedule");
        schedule.setAccessible(true);
        var tick = GroundCleanup.class.getDeclaredMethod("tick", MinecraftServer.class);
        tick.setAccessible(true);
        try {
            for (int seconds = 10; seconds >= 1; seconds--) {
                messages.clear();
                schedule.set(null, new GroundCleanupSchedule(System.currentTimeMillis()
                    - GroundCleanupSchedule.PERIOD_MS + seconds * 1000L - 10));
                tick.invoke(null, server);
                require(messages.equals(List.of("Clean-up in " + seconds)), "Wrong countdown: " + messages);
            }
            GroundCleanup.clear(server);
            var world = server.getOverworld();
            world.spawnEntity(new ItemEntity(world, 0.5, 100, 0.5, new ItemStack(Items.DIAMOND, 64)));
            world.spawnEntity(new ItemEntity(world, 2.5, 100, 0.5, new ItemStack(Items.IRON_INGOT, 3)));
            messages.clear();
            schedule.set(null, new GroundCleanupSchedule(System.currentTimeMillis()
                - GroundCleanupSchedule.PERIOD_MS - 10));
            tick.invoke(null, server);
            require(messages.equals(List.of("Ground cleanup removed 67 items.")), "Wrong cleanup total: " + messages);
            require(GroundCleanup.clear(server) == 0, "Ground items were not removed");
        } finally {
            server.getPlayerManager().getPlayerList().remove(player);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifySales(MinecraftServer server) throws ReflectiveOperationException {
        var seller = player(server, server.getOverworld(), "SellerFixture");
        var buyer = player(server, server.getOverworld(), "BuyerFixture");
        var mapField = PlayerManager.class.getDeclaredField("playerMap");
        mapField.setAccessible(true);
        var players = (Map<UUID, ServerPlayerEntity>)mapField.get(server.getPlayerManager());
        players.put(seller.getUuid(), seller);
        Object economy = Class.forName("com.gangs.gangshop.GangShopMod").getField("ECONOMY").get(null);
        var balance = economy.getClass().getMethod("getBalance", UUID.class);
        economy.getClass().getMethod("set", UUID.class, long.class).invoke(economy, buyer.getUuid(), 1000L);
        Class<?> storeClass = Class.forName("com.gangs.gangsales.SalesStore");
        Object store = storeClass.getConstructor().newInstance();
        storeClass.getMethod("load", MinecraftServer.class).invoke(store, server);
        try {
            for (String name : List.of("Diamond", "Fixture Custom Diamond")) {
                var stack = new ItemStack(Items.DIAMOND, 3);
                if (name.startsWith("Fixture")) stack.setCustomName(Text.literal(name));
                seller.setStackInHand(Hand.MAIN_HAND, stack);
                Object listing = storeClass.getMethod("create", ServerPlayerEntity.class, int.class, long.class)
                    .invoke(store, seller, 3, 100L);
                UUID id = (UUID)listing.getClass().getMethod("getId").invoke(listing);
                messages.clear();
                Object result = storeClass.getMethod("purchase", ServerPlayerEntity.class, UUID.class).invoke(store, buyer, id);
                require(result.toString().equals("SUCCESS"), "Actual purchase failed: " + result);
                require(messages.equals(List.of("BuyerFixture has bought your " + name + " for 100 Gang Bucks.")),
                    "Seller got wrong item name: " + messages);
            }
            require(((Long)balance.invoke(economy, buyer.getUuid())) == 800L, "Buyer charged wrong total");
            require(((Long)balance.invoke(economy, seller.getUuid())) == 200L, "Seller received wrong total");
            require(buyer.getInventory().count(Items.DIAMOND) == 6, "Buyer did not receive purchased stacks");
        } finally {
            players.remove(seller.getUuid());
        }
    }

    private static void verifyBosses(ServerWorld world) {
        EntityType<? extends net.minecraft.entity.mob.MobEntity> type =
            world.getRegistryKey().getValue().toString().equals(GoldClaimMod.RSW_THE_END_DIMENSION)
                ? EntityType.ENDERMAN
                : world.getRegistryKey().getValue().toString().equals(GoldClaimMod.RSW_NETHER_DIMENSION)
                    ? EntityType.BLAZE : EntityType.ZOMBIE;
        int bosses = 0;
        for (int index = 0; index < 2000; index++) {
            var mob = type.create(world);
            mob.refreshPositionAndAngles(0.5, 100, 0.5, 0, 0);
            mob.getRandom().setSeed(index * 691L);
            mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.NATURAL, null, null);
            var capability = ((BossCapEntity)mob).getBossCap();
            if (capability != null) {
                bosses++;
                require(capability.isBoss() && mob.hasCustomName() && mob.getMaxHealth() > 20,
                    "Roaming boss lacks real boss identity/stats");
                var nbt = new net.minecraft.nbt.NbtCompound();
                mob.writeNbt(nbt);
                require(nbt.contains("brutalbosses:bosscap"), "Boss identity was not persisted: " + nbt);
                var reloaded = type.create(world);
                reloaded.readNbt(nbt);
                require(((BossCapEntity)reloaded).getBossCap().isBoss(), "Saved roaming boss lost its identity");
                require(!com.brutalbosses.event.EventHandler.protectedBlocks.containsKey(mob.getBlockPos()),
                    "Roaming boss protected a random block after spawn or reload");
            }
        }
        require(bosses >= 5 && bosses <= 40, "Roaming boss frequency outside 1% sample bounds: " + bosses);
        var summoned = type.create(world);
        summoned.initialize(world, world.getLocalDifficulty(summoned.getBlockPos()), SpawnReason.COMMAND, null, null);
        require(((BossCapEntity)summoned).getBossCap() == null, "Non-natural spawn became a roaming boss");
        System.out.println("Roaming bosses in " + world.getRegistryKey().getValue() + ": " + bosses + "/2000");
    }

    private static ServerWorld world(MinecraftServer server, String id) {
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(id)));
    }

    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name) {
        var player = new ServerPlayerEntity(server, world, new GameProfile(UUID.randomUUID(), name)) {
            @Override public void sendMessage(Text message, boolean overlay) { messages.add(message.getString()); }
            @Override public void sendMessageToClient(Text message, boolean overlay) { messages.add(message.getString()); }
            @Override public boolean isDisconnected() { return false; }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) {
                if (packet instanceof GameMessageS2CPacket message) messages.add(message.content().getString());
            }
        };
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
