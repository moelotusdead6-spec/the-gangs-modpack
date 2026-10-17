package com.elysium.goldclaim;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class KitRewardSmokeTest {
    private static boolean verified;

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (verified || server.getTicks() < 40) return;
            verified = true;
            verify(server);
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: every reward item exists in actual installed mod registries; all four kits deliver "
                    + "through SkiesKits COMMAND_CONSOLE; successful claims consumed exactly once; full-inventory, "
                    + "ineligible failures reach player despite silent console; failed claims not consumed.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record kit reward smoke result", failure);
            }
            server.stop(false);
        });
    }

    private static void verify(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        Path tables = Path.of("smoke-world", "datapacks", "gangs_kits", "data", "gangs", "loot_tables", "kits");
        try (var files = Files.list(tables)) {
            for (Path file : files.toList()) {
                validateItems(JsonParser.parseString(Files.readString(file)), missing);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect kit tables", failure);
        }
        try {
            validateItems(JsonParser.parseString(Files.readString(Path.of("config", "randomcollections", "config.json"))), missing);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect monthly reward collections", failure);
        }
        require(missing.isEmpty(), "Unregistered reward items: " + missing);
        List<String> messages = new ArrayList<>();
        ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(),
                new GameProfile(UUID.randomUUID(), "Kit" + UUID.randomUUID().toString().substring(0, 8))) {
            @Override public void sendMessage(Text text, boolean overlay) {
                messages.add(text.getString());
            }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        server.getPlayerManager().getPlayerList().add(player);
        server.getOverworld().onPlayerConnected(player);
        KitService service = new KitService();
        service.initialize(server);
        service.join(player);
        var scoreboard = server.getScoreboard();
        String name = player.getGameProfile().getName();
        scoreboard.getPlayerScore(name, scoreboard.getNullableObjective("gangs_cycle_start"))
            .setScore((int)(System.currentTimeMillis() / 1000 - 22L * 86400L));
        String[] keys = {"w1", "w2", "w3", "m"};
        String[] names = {"weekly_1", "weekly_2", "weekly_3", "monthly"};
        for (int kit = 0; kit < 4; kit++) {
            require(server.getLootManager().getLootTable(new Identifier("gangs", "kits/" + names[kit])) != LootTable.EMPTY,
                "Missing table " + names[kit]);
            player.getInventory().clear();
            messages.clear();
            dispatchKit(kit + 1 + "_" + names[kit], player);
            require(!player.getInventory().isEmpty(), "SkiesKits did not deliver " + names[kit] + ": " + messages);
            require(scoreboard.getPlayerScore(name, scoreboard.getNullableObjective("gangs_claimed_" + keys[kit]))
                .getScore() == 1, "Successful claim not recorded");
            require(messages.stream().anyMatch(message -> message.contains("kit claimed!")), "No success message");
            List<ItemStack> before = snapshot(player);
            messages.clear();
            require(service.claim(player.getCommandSource().withSilent(), kit) == 0, "Duplicate claim accepted");
            require(sameInventory(before, player), "Duplicate claim changed inventory");
            require(messages.stream().anyMatch(message -> message.contains("not available")), "Silent duplicate error");
        }
        scoreboard.getPlayerScore(name, scoreboard.getNullableObjective("gangs_claimed_w1")).setScore(0);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        List<ItemStack> before = snapshot(player);
        messages.clear();
        require(service.claim(player.getCommandSource().withSilent(), 0) == 0, "Full inventory claim accepted");
        require(sameInventory(before, player), "Failed claim modified inventory");
        require(scoreboard.getPlayerScore(name, scoreboard.getNullableObjective("gangs_claimed_w1")).getScore() == 0,
            "Failed claim consumed");
        require(messages.stream().anyMatch(message -> message.contains("inventory space")), "Silent inventory error");
        server.getPlayerManager().getPlayerList().remove(player);
        server.getOverworld().removePlayer(player, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
    }

    private static void dispatchKit(String key, ServerPlayerEntity player) {
        try {
            Class<?> manager = Class.forName("com.pokeskies.skieskits.config.ConfigManager");
            Object companion = manager.getField("Companion").get(null);
            Object configured = companion.getClass().getMethod("getKITS").invoke(companion);
            require(configured instanceof Map<?, ?>, "SkiesKits configuration is not a kit map");
            Object kit = ((Map<?, ?>)configured).get(key);
            require(kit != null, "SkiesKits did not load " + key);
            kit.getClass().getMethod("claim", String.class, ServerPlayerEntity.class, boolean.class, boolean.class)
                .invoke(kit, key, player, false, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot exercise installed SkiesKits claim", failure);
        }
    }

    private static void validateItems(JsonElement element, List<String> missing) {
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").getAsString().equals("minecraft:item")) {
                String name = object.get("name").getAsString();
                if (!Registries.ITEM.containsId(new Identifier(name))) missing.add(name);
            }
            for (var entry : object.entrySet()) validateItems(entry.getValue(), missing);
        } else if (element.isJsonArray()) {
            for (var entry : element.getAsJsonArray()) validateItems(entry, missing);
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String command = element.getAsString();
            if (command.startsWith("give %player% ")) {
                String item = command.split("\\s+")[2];
                if (!Registries.ITEM.containsId(new Identifier(item))) missing.add(item);
            }
        }
    }

    private static List<ItemStack> snapshot(ServerPlayerEntity player) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) result.add(player.getInventory().getStack(slot).copy());
        return result;
    }

    private static boolean sameInventory(List<ItemStack> before, ServerPlayerEntity player) {
        for (int slot = 0; slot < 36; slot++) {
            if (!ItemStack.areEqual(before.get(slot), player.getInventory().getStack(slot))) return false;
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
