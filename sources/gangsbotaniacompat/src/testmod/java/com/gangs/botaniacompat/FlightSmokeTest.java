package com.gangs.botaniacompat;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public final class FlightSmokeTest implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                verify(server);
                Files.writeString(Path.of("botania-flight-smoke-passed.txt"),
                    "PASS: Tiara retained; flight (including Eye/NBT override), glide and dash blocked; "
                    + "other sources' flight permissions preserved in all four game modes; "
                    + "cosmetic NBT and other items preserved; equip notice delivered.\n");
                server.stop(false);
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Botania flight smoke failed", failure);
            }
        });
    }

    private static void verify(MinecraftServer server) throws ReflectiveOperationException {
        var tiaraId = new Identifier(BotaniaCompat.TIARA);
        require(Registries.ITEM.containsId(tiaraId), "Pinned Botania Tiara missing");
        var item = Registries.ITEM.get(tiaraId);
        var type = item.getClass();
        var predicate = type.getDeclaredMethod("shouldPlayerHaveFlight", PlayerEntity.class);
        predicate.setAccessible(true);
        // Null proves the injection returns before upstream equipment, Eye or mana checks.
        require(Boolean.FALSE.equals(predicate.invoke(null, new Object[]{null})), "Flight predicate not intercepted");
        var wornTick = type.getMethod("onWornTick", ItemStack.class, LivingEntity.class);
        var equipped = type.getMethod("onEquipped", ItemStack.class, LivingEntity.class);
        var player = new FixturePlayer(server, server.getOverworld());
        var setGameMode = Arrays.stream(ServerPlayerInteractionManager.class.getDeclaredMethods())
            .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{GameMode.class, GameMode.class}))
            .findFirst().orElseThrow();
        setGameMode.setAccessible(true);
        for (GameMode mode : GameMode.values()) {
            setGameMode.invoke(player.interactionManager, mode, null);
            for (boolean allowFlying : new boolean[]{false, true}) {
                for (boolean alreadyFlying : new boolean[]{false, true}) {
                    var stack = new ItemStack(item);
                    var nbt = stack.getOrCreateNbt();
                    nbt.putInt("variant", 3);
                    nbt.putBoolean("infiniteFlight", true);
                    nbt.putBoolean("flying", true);
                    nbt.putBoolean("gliding", true);
                    nbt.putBoolean("boostPending", true);
                    nbt.putInt("dashCooldown", 10);
                    nbt.putInt("timeLeft", 0);
                    stack.setCustomName(Text.literal("Keep these wings"));
                    player.getAbilities().allowFlying = allowFlying;
                    player.getAbilities().flying = alreadyFlying;
                    player.getAbilities().invulnerable = true;
                    player.setOnGround(false);
                    player.setSneaking(true);
                    player.setSprinting(true);
                    player.setVelocity(0.25, -1, 0.5);
                    player.fallDistance = 20;
                    player.updates = 0;
                    wornTick.invoke(item, stack, player);
                    require(player.getVelocity().equals(new Vec3d(0.25, -1, 0.5)), "Glide/dash changed velocity: " + mode);
                    require(player.fallDistance == 20, "Tiara changed fall distance: " + mode);
                    require(nbt.getInt("variant") == 3 && stack.getName().getString().equals("Keep these wings"),
                        "Cosmetics modified");
                    require(nbt.getInt("timeLeft") == 0 && stack.getCount() == 1, "Item or flight-time NBT removed");
                    require(!nbt.contains("flying") && !nbt.contains("gliding") && !nbt.contains("boostPending"),
                        "Stale movement flags survived");
                    require(player.getAbilities().invulnerable, "Unrelated invulnerability changed");
                    require(player.getAbilities().allowFlying == allowFlying, "Other flight permission changed: " + mode);
                    require(player.getAbilities().flying == alreadyFlying, "Other flying state changed: " + mode);
                    require(player.updates == 0, "Unrelated ability packet sent: " + mode);
                    wornTick.invoke(item, stack, player);
                    require(player.updates == 0, "Unrelated ability packet sent on repeat tick");
                    equipped.invoke(item, stack, player);
                    require(BotaniaCompat.NOTICE.equals(player.notice), "Equip notice missing");
                }
            }
        }
        require(!new ItemStack(Registries.ITEM.get(new Identifier("botania:flugel_eye"))).isEmpty(), "Eye removed");
        require(!new ItemStack(Registries.ITEM.get(new Identifier("minecraft:elytra"))).isEmpty(), "Elytra removed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class FixturePlayer extends ServerPlayerEntity {
        int updates;
        String notice;

        FixturePlayer(MinecraftServer server, ServerWorld world) {
            super(server, world, new GameProfile(UUID.randomUUID(), "FlightFixture"));
        }

        @Override
        public void sendAbilitiesUpdate() {
            updates++;
        }

        @Override
        public void sendMessage(Text message, boolean overlay) {
            notice = message.getString();
        }
    }
}
