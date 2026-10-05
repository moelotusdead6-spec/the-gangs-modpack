package com.elysium.goldclaim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KitService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Kits");
    static final long CYCLE_SECONDS = 30L * 86400L;
    private static final String[] KEYS = {"w1", "w2", "w3", "m"};
    private static final String[] TABLES = {"weekly_1", "weekly_2", "weekly_3", "monthly"};
    private final Map<UUID, Integer> permissionStates = new HashMap<>();
    private final Map<String, UUID> knownPlayers = new HashMap<>();
    private JsonObject collections;
    private List<String> sweep = List.of();
    private int sweepIndex;

    static long cycleStart(long start, long now) {
        if (start <= 0 || start > now) {
            return now;
        }
        return start + ((now - start) / CYCLE_SECONDS) * CYCLE_SECONDS;
    }

    static boolean eligible(int kit, long elapsed, boolean claimed) {
        return !claimed && elapsed >= kit * 7L * 86400L;
    }

    public void initialize(MinecraftServer server) {
        this.permissionStates.clear();
        this.reloadCollections();
        for (String key : KEYS) {
            objective(server.getScoreboard(), "gangs_claimed_" + key);
            objective(server.getScoreboard(), "gangs_perm_" + key);
        }
        objective(server.getScoreboard(), "gangs_cycle_start");
        if (Files.exists(Path.of("usercache.json"))) {
            try (var reader = Files.newBufferedReader(Path.of("usercache.json"))) {
                for (JsonElement entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject player = entry.getAsJsonObject();
                    this.knownPlayers.put(player.get("name").getAsString(), UUID.fromString(player.get("uuid").getAsString()));
                }
            } catch (IOException | RuntimeException failure) {
                LOGGER.error("Could not read offline kit player names.", failure);
            }
        }
    }

    public void reloadCollections() {
        try (var reader = Files.newBufferedReader(Path.of("config", "randomcollections", "config.json"))) {
            this.collections = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("collections");
        } catch (IOException | RuntimeException failure) {
            this.collections = null;
            LOGGER.error("Monthly reward collections could not be loaded; monthly claims will be rejected without consuming them.", failure);
        }
    }

    public void join(ServerPlayerEntity player) {
        this.knownPlayers.put(player.getGameProfile().getName(), player.getUuid());
        this.permissionStates.remove(player.getUuid());
        this.reconcile(player.getServer(), player.getGameProfile().getName(), player.getUuid());
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            this.reconcile(server, player.getGameProfile().getName(), player.getUuid());
        }
        if (this.sweepIndex >= this.sweep.size()) {
            this.sweep = new ArrayList<>(this.knownPlayers.keySet());
            this.sweepIndex = 0;
        }
        for (int checked = 0; checked < 32 && this.sweepIndex < this.sweep.size(); checked++) {
            String name = this.sweep.get(this.sweepIndex++);
            if (server.getPlayerManager().getPlayer(this.knownPlayers.get(name)) == null) {
                this.reconcile(server, name, this.knownPlayers.get(name));
            }
        }
    }

    private int reconcile(MinecraftServer server, String name, UUID uuid) {
        ServerScoreboard scoreboard = server.getScoreboard();
        long now = System.currentTimeMillis() / 1000L;
        var startScore = scoreboard.getPlayerScore(name, objective(scoreboard, "gangs_cycle_start"));
        long previous = startScore.getScore();
        long start = cycleStart(previous, now);
        if (start != previous) {
            startScore.setScore((int)start);
            for (String key : KEYS) {
                scoreboard.getPlayerScore(name, objective(scoreboard, "gangs_claimed_" + key)).setScore(0);
            }
        }
        int state = 0;
        for (int kit = 0; kit < KEYS.length; kit++) {
            boolean available = eligible(kit, now - start, scoreboard.getPlayerScore(name, objective(scoreboard, "gangs_claimed_" + KEYS[kit])).getScore() != 0);
            scoreboard.getPlayerScore(name, objective(scoreboard, "gangs_perm_" + KEYS[kit])).setScore(available ? 1 : 0);
            if (available) {
                state |= 1 << kit;
            }
        }
        if (!Integer.valueOf(state).equals(this.permissionStates.get(uuid))) {
            this.updatePermissions(uuid, state);
        }
        return state;
    }

    private void updatePermissions(UUID uuid, int state) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            this.permissionStates.put(uuid, state);
            luckPerms.getUserManager().modifyUser(uuid, user -> {
                for (int kit = 0; kit < KEYS.length; kit++) {
                    String permission = "goldclaim.kit." + KEYS[kit];
                    user.data().clear(node -> node.getKey().equals(permission));
                    user.data().add(Node.builder(permission).value((state & (1 << kit)) != 0).build());
                }
            }).exceptionally(failure -> {
                LOGGER.error("Could not update kit permissions for {}.", uuid, failure);
                return null;
            });
        } catch (IllegalStateException failure) {
            LOGGER.error("LuckPerms unavailable for kit reconciliation.", failure);
        }
    }

    public int claim(ServerCommandSource source, int kit) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("Execute this command as the claiming player."));
            return 0;
        }
        String name = player.getGameProfile().getName();
        if ((this.reconcile(source.getServer(), name, player.getUuid()) & (1 << kit)) == 0) {
            source.sendError(Text.literal("This kit is not available in your current 30-day cycle."));
            return 0;
        }
        try {
            LootTable table = source.getServer().getLootManager().getLootTable(new Identifier("gangs", "kits/" + TABLES[kit]));
            if (table == LootTable.EMPTY) {
                throw new IllegalStateException("Missing or invalid kit loot table: " + TABLES[kit]);
            }
            LootContextParameterSet context = new LootContextParameterSet.Builder(player.getServerWorld())
                .add(LootContextParameters.ORIGIN, player.getPos()).add(LootContextParameters.THIS_ENTITY, player)
                .luck(player.getLuck()).build(LootContextTypes.CHEST);
            List<ItemStack> rewards = new ArrayList<>(table.generateLoot(context));
            if (rewards.isEmpty()) {
                throw new IllegalStateException("Kit loot table generated no rewards");
            }
            if (kit == 3) {
                this.addCollection(rewards, "monthly_bow_staff");
                this.addCollection(rewards, "monthly_trinket_gem_totem");
            }
            List<ItemStack> inventory = new ArrayList<>();
            for (int slot = 0; slot < 36; slot++) {
                inventory.add(player.getInventory().getStack(slot).copy());
            }
            for (ItemStack reward : rewards) {
                ItemStack remaining = reward.copy();
                for (ItemStack stored : inventory) {
                    if (!stored.isEmpty() && ItemStack.canCombine(stored, remaining)) {
                        int moved = Math.min(remaining.getCount(), Math.max(0, stored.getMaxCount() - stored.getCount()));
                        stored.increment(moved);
                        remaining.decrement(moved);
                    }
                }
                for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                    if (inventory.get(slot).isEmpty()) {
                        inventory.set(slot, remaining.copyWithCount(Math.min(remaining.getCount(), remaining.getMaxCount())));
                        remaining.decrement(inventory.get(slot).getCount());
                    }
                }
                if (!remaining.isEmpty()) {
                    source.sendError(Text.literal("Not enough inventory space for the complete kit. Your claim remains available."));
                    return 0;
                }
            }
            for (int slot = 0; slot < inventory.size(); slot++) {
                player.getInventory().setStack(slot, inventory.get(slot));
            }
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
            source.getServer().getScoreboard().getPlayerScore(name, objective(source.getServer().getScoreboard(), "gangs_claimed_" + KEYS[kit])).setScore(1);
            this.reconcile(source.getServer(), name, player.getUuid());
            player.sendMessage(Text.literal("[Kits] " + TABLES[kit].replace('_', ' ') + " kit claimed!"), false);
            return 1;
        } catch (RuntimeException failure) {
            LOGGER.error("Kit {} could not be generated for {}; no rewards were delivered and no claim consumed.", TABLES[kit], name, failure);
            source.sendError(Text.literal("Kit rewards could not be loaded. Your claim remains available; please notify an admin."));
            return 0;
        }
    }

    private void addCollection(List<ItemStack> rewards, String key) {
        if (this.collections == null || !this.collections.has(key)) {
            throw new IllegalStateException("Missing reward collection " + key);
        }
        JsonArray options = this.collections.getAsJsonArray(key);
        int total = 0;
        for (JsonElement option : options) {
            total = Math.addExact(total, option.getAsJsonObject().get("weight").getAsInt());
            for (JsonElement command : option.getAsJsonObject().getAsJsonArray("commands")) {
                parseReward(command.getAsString());
            }
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (JsonElement option : options) {
            JsonObject value = option.getAsJsonObject();
            roll -= value.get("weight").getAsInt();
            if (roll < 0) {
                for (JsonElement command : value.getAsJsonArray("commands")) {
                    rewards.add(parseReward(command.getAsString()));
                }
                return;
            }
        }
    }

    private static ItemStack parseReward(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length != 4 || !parts[0].equals("give") || !parts[1].equals("%player%")) {
            throw new IllegalArgumentException("Unsupported monthly reward command: " + command);
        }
        Identifier item = Identifier.tryParse(parts[2]);
        int count = Integer.parseInt(parts[3]);
        if (item == null || !Registries.ITEM.containsId(item) || count <= 0) {
            throw new IllegalArgumentException("Invalid monthly reward: " + command);
        }
        return new ItemStack(Registries.ITEM.get(item), count);
    }

    private static ScoreboardObjective objective(ServerScoreboard scoreboard, String name) {
        ScoreboardObjective result = scoreboard.getNullableObjective(name);
        return result != null ? result : scoreboard.addObjective(name, ScoreboardCriterion.DUMMY, Text.literal(name), ScoreboardCriterion.RenderType.INTEGER);
    }
}