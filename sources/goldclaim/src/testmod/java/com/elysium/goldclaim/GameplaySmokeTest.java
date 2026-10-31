package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

final class GameplaySmokeTest {
    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                if (Boolean.getBoolean("goldclaim.spongeBaseline")) {
                    verifySponges(server, 64);
                    Files.writeString(Path.of("sponge-baseline-passed.txt"), "PASS: original absorption pass removes 64 water blocks.\n");
                    server.stop(false);
                    return;
                }
                verifySuggestions(server);
                verifySponges(server, 128);
                verifyPortals(server);
                verifyExemptions();
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: player-name completion, 128-water absorption passes, real Alex portals at current borders, "
                    + "Fiw exemptions and updated Better End/BCLib startup.\n");
            } catch (Exception failure) {
                throw new IllegalStateException("Gameplay smoke failed", failure);
            }
            server.stop(false);
        });
    }

    private static void verifySuggestions(MinecraftServer server) {
        var player = new ServerPlayerEntity(server, server.getOverworld(),
            new GameProfile(UUID.randomUUID(), "CompletionTarget"));
        server.getPlayerManager().getPlayerList().add(player);
        var dispatcher = server.getCommandManager().getDispatcher();
        dispatcher.register(CommandManager.literal("completionfixture")
            .then(CommandManager.argument("player", StringArgumentType.word()).executes(ctx -> 1))
            .then(CommandManager.literal("home")
                .then(CommandManager.argument("name", StringArgumentType.word()).executes(ctx -> 1))));
        for (String command : List.of("claim trust", "claim trust interact", "claim trust manager",
                "claim untrust", "claim untrust interact", "claim untrust manager",
                "claim admin trust", "claim admin untrust", "goldclaimadmin trust",
                "goldclaimadmin untrust", "gcadmin trust", "gcadmin untrust", "completionfixture")) {
            for (String prefix : List.of("", "cOmPlEtIoNt")) {
                var suggestions = dispatcher.getCompletionSuggestions(
                    dispatcher.parse(command + " " + prefix, server.getCommandSource())).join();
                require(suggestions.getList().stream().anyMatch(s -> s.getText().equals("CompletionTarget")),
                    "Player suggestion missing: " + command + " " + prefix);
            }
        }
        var home = dispatcher.getCompletionSuggestions(
            dispatcher.parse("completionfixture home ", server.getCommandSource())).join();
        require(home.getList().isEmpty(), "Home names must not become player suggestions");
        server.getPlayerManager().getPlayerList().remove(player);
    }

    private static void verifySponges(MinecraftServer server, int expected) throws Exception {
        var world = server.getOverworld();
        BlockPos center = new BlockPos(0, 220, 0);
        world.setBlockState(center, Blocks.STONE.getDefaultState(), 2);
        int before = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-6, -6, -6), center.add(6, 6, 6))) {
            if (pos.equals(center)) continue;
            world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
            before++;
        }
        // A stone origin isolates one absorption pass from vanilla's recursive sponge neighbor updates.
        var absorb = java.util.Arrays.stream(net.minecraft.block.SpongeBlock.class.getDeclaredMethods())
            .filter(method -> method.getReturnType() == boolean.class
                && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{World.class, BlockPos.class}))
            .findFirst().orElseThrow();
        absorb.setAccessible(true);
        require((boolean)absorb.invoke(Blocks.SPONGE, world, center), "Water absorption failed");
        int after = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-6, -6, -6), center.add(6, 6, 6))) {
            if (world.getBlockState(pos).isOf(Blocks.WATER)) after++;
        }
        require(before - after == expected, "Absorption pass must remove exactly " + expected
            + " water blocks, got " + (before - after));
        require(world.getBlockState(center.east(6)).isOf(Blocks.WATER), "Sponge must preserve its limited reach");
        world.setBlockState(center.up(20), Blocks.SPONGE.getDefaultState(), 3);
        require(world.getBlockState(center.up(20)).isOf(Blocks.SPONGE), "Dry sponge must stay dry without water");
        world.setBlockState(center.up(20).east(), Blocks.WATER.getDefaultState(), 3);
        require(world.getBlockState(center.up(20)).isOf(Blocks.WET_SPONGE), "Absorbing sponge must become wet");
    }

    private static void verifyPortals(MinecraftServer server) throws Exception {
        var world = server.getOverworld();
        var border = world.getWorldBorder();
        border.setCenter(1250, -750);
        border.setSize(7000);
        Class<?> portalType = Class.forName("com.github.alexthe666.alexsmobs.entity.EntityVoidPortal");
        Class<?> carverType = Class.forName("com.github.alexthe666.alexsmobs.item.ItemDimensionalCarver");
        Item carver = Registries.ITEM.get(new Identifier("alexsmobs:shattered_dimensional_carver"));
        require(carverType.isInstance(carver), "Real Alex's Mobs carver is missing");
        Object portal = portalType.getConstructor(World.class, carverType).newInstance(world, carver);
        portalType.getMethod("setShattered", boolean.class).invoke(portal, true);
        portalType.getField("exitDimension").set(portal, world.getRegistryKey());
        var setter = portalType.getMethod("setDestination", BlockPos.class);
        var getter = portalType.getMethod("getDestination");
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos far = new BlockPos(1250, 240, -750).offset(direction, 1_000_000);
            setter.invoke(portal, far);
            BlockPos actual = (BlockPos)getter.invoke(portal);
            require(border.contains(actual) && actual.getY() == 240,
                "Million-block portal destination escaped border: " + actual);
        }
        setter.invoke(portal, new BlockPos(1_000_000, 240, -1_000_000));
        border.setSize(32);
        require(border.contains((BlockPos)getter.invoke(portal)), "Saved portal ignored a shrinking border");
        portalType.getMethod("setShattered", boolean.class).invoke(portal, false);
        require(getter.invoke(portal).equals(new BlockPos(1_000_000, 240, -1_000_000)),
            "Ordinary carver behavior changed");
        ((Entity)portal).discard();
        border.setCenter(0, 0);
        border.setSize(60_000_000);
    }

    private static void verifyExemptions() throws Exception {
        Class<?> api = Class.forName("dev.fiw.modsapi.FiwModsApi");
        Object engine = api.getMethod("engine").invoke(null);
        var resolve = engine.getClass().getMethod("isBypassed", String.class, UUID.class);
        for (String id : List.of("3b3c1ca0-9968-4257-9105-cd17dc7843cf", "3637a9fb-d4fe-4eee-a7c8-24517b22e064")) {
            require((boolean)resolve.invoke(engine, "RenamedAccount", UUID.fromString(id)), "Missing Fiw bypass: " + id);
        }
        require(!(boolean)resolve.invoke(engine, "OrdinaryPlayer", UUID.randomUUID()), "Global Fiw bypass enabled");
        require(!(boolean)resolve.invoke(engine, "Oux_y", UUID.randomUUID()), "Name alone must not grant an exemption");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
