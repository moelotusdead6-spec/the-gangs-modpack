package com.elysium.goldclaim;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.elysium.goldclaim.mixin.PlayerManagerInvoker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MailboxService {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/Mailbox");
    private static final Set<String> STAFF = Set.of("owner", "admin", "mod", "moderator");
    private static final int PAGE_SIZE = 45;
    record Mail(UUID id, ItemStack stack) {}

    static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registries) {
        dispatcher.register(CommandManager.literal("mailbox")
            .executes(ctx -> open(ctx.getSource().getPlayerOrThrow(), 0))
            .then(CommandManager.literal("send").requires(MailboxService::canSend)
                .then(CommandManager.literal("all")
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registries))
                        .executes(ctx -> {
                            try {
                                return sendTargets(ctx.getSource(), knownPlayers(ctx.getSource()),
                                    ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, true));
                            } catch (IOException failure) {
                                LOGGER.error("Could not load known mailbox recipients", failure);
                                ctx.getSource().sendError(Text.literal("[Mailbox] Known-player list could not be read; no rewards sent."));
                                return 0;
                            }
                        })))
                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                    .suggests((ctx, builder) -> {
                        try {
                            List<String> names = new ArrayList<>(knownPlayers(ctx.getSource()).stream()
                                .map(GameProfile::getName).toList());
                            names.add("@a");
                            names.add("all");
                            return CommandSource.suggestMatching(names, builder);
                        } catch (IOException failure) {
                            LOGGER.error("Could not load offline mailbox name suggestions", failure);
                            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                                Text.literal("[Mailbox] Offline name suggestions are unavailable; contact staff.")).create();
                        }
                    })
                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registries))
                        .executes(ctx -> sendTargets(ctx.getSource(),
                            GameProfileArgumentType.getProfileArgument(ctx, "player"),
                            ItemStackArgumentType.getItemStackArgument(ctx, "item").createStack(1, true)))))));
    }

    private static Collection<GameProfile> knownPlayers(ServerCommandSource source) throws IOException {
        var profiles = new LinkedHashMap<UUID, GameProfile>();
        Path cache = Path.of("usercache.json");
        if (Files.exists(cache)) {
            try (var reader = Files.newBufferedReader(cache)) {
                for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    var entry = element.getAsJsonObject();
                    UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
                    profiles.put(uuid, new GameProfile(uuid, entry.get("name").getAsString()));
                }
            } catch (RuntimeException failure) {
                throw new IOException("Invalid known-player cache", failure);
            }
        }
        for (var player : source.getServer().getPlayerManager().getPlayerList()) {
            profiles.put(player.getUuid(), player.getGameProfile());
        }
        return profiles.values();
    }

    private static int sendTargets(ServerCommandSource source, Collection<GameProfile> targets, ItemStack stack) {
        if (targets.isEmpty()) {
            source.sendError(Text.literal("[Mailbox] No matching known players; no rewards sent."));
            return 0;
        }
        int sent = 0;
        for (GameProfile target : targets) sent += send(source, target, stack, targets.size() == 1);
        if (targets.size() > 1) {
            int successful = sent;
            source.sendFeedback(() -> Text.literal("[Mailbox] Sent one reward each to " + successful
                + " player(s); failed: " + (targets.size() - successful) + "."), true);
        }
        return sent;
    }

    static boolean canSend(ServerCommandSource source) {
        if (source.getPlayer() == null) return source.hasPermissionLevel(2);
        try {
            var user = LuckPermsProvider.get().getUserManager().getUser(source.getPlayer().getUuid());
            if (user != null) {
                var permission = user.getCachedData().getPermissionData(user.getQueryOptions())
                    .checkPermission("goldclaim.mailbox.send");
                if (permission != Tristate.UNDEFINED) return permission.asBoolean();
                if (STAFF.contains(user.getPrimaryGroup()) || user.getInheritedGroups(user.getQueryOptions())
                        .stream().anyMatch(group -> STAFF.contains(group.getName()))) return true;
            }
        } catch (IllegalStateException | NoClassDefFoundError unavailable) {
            LOGGER.error("LuckPerms unavailable for mailbox send; checking operator level only.", unavailable);
        }
        return source.hasPermissionLevel(2);
    }

    static Path file(ServerPlayerEntity player) {
        return file(player.getCommandSource(), player.getUuid());
    }

    private static Path file(ServerCommandSource source, UUID uuid) {
        return source.getServer().getSavePath(WorldSavePath.ROOT).resolve("gangs-mailbox").resolve(uuid + ".json");
    }

    static List<Mail> read(Path path) throws IOException {
        List<Mail> mail = new ArrayList<>();
        if (!Files.exists(path)) return mail;
        try (var reader = Files.newBufferedReader(path)) {
            JsonArray entries = JsonParser.parseReader(reader).getAsJsonArray();
            Set<UUID> ids = new java.util.HashSet<>();
            for (var element : entries) {
                JsonObject entry = element.getAsJsonObject();
                UUID id = UUID.fromString(entry.get("id").getAsString());
                NbtCompound nbt = StringNbtReader.parse(entry.get("stack").getAsString());
                ItemStack stack = ItemStack.fromNbt(nbt);
                if (stack.isEmpty() || stack.getCount() > stack.getMaxCount() || !ids.add(id)) {
                    throw new IOException("Invalid or unavailable mailbox item: " + id);
                }
                mail.add(new Mail(id, stack));
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException failure) {
            throw new IOException("Invalid mailbox data at " + path, failure);
        }
        return mail;
    }

    static void write(Path path, List<Mail> mail) throws IOException {
        JsonArray entries = new JsonArray();
        for (Mail reward : mail) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", reward.id().toString());
            entry.addProperty("stack", reward.stack().writeNbt(new NbtCompound()).toString());
            entries.add(entry);
        }
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "mailbox-", ".tmp");
        try {
            Files.writeString(temporary, new Gson().toJson(entries));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int send(ServerCommandSource source, GameProfile recipient, ItemStack stack, boolean feedback) {
        if (!canSend(source)) {
            source.sendError(Text.literal("[Mailbox] Only Mod, Admin and Owner can send rewards."));
            return 0;
        }
        if (stack.isEmpty()) {
            source.sendError(Text.literal("[Mailbox] That item cannot be mailed."));
            return 0;
        }
        try {
            Path path = file(source, recipient.getId());
            List<Mail> mail = read(path);
            mail.add(new Mail(UUID.randomUUID(), stack.copy()));
            write(path, mail);
            if (feedback) source.sendFeedback(() -> Text.literal("[Mailbox] Sent ").append(stack.toHoverableText())
                .append(Text.literal(" to " + recipient.getName() + ".")), true);
            ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(recipient.getId());
            if (online != null) notify(online, mail.size());
            return 1;
        } catch (IOException failure) {
            LOGGER.error("Could not send mailbox reward to {}", recipient.getId(), failure);
            source.sendError(Text.literal("[Mailbox] Reward was not sent to " + recipient.getName()
                + ": mailbox storage could not be saved."));
            return 0;
        }
    }

    static void join(ServerPlayerEntity player) {
        try {
            notify(player, read(file(player)).size());
        } catch (IOException failure) {
            error(player, failure);
        }
    }

    private static void notify(ServerPlayerEntity player, int count) {
        if (count > 0) player.sendMessage(Text.literal("[Mailbox] You have " + count
            + " unclaimed reward(s). Use /mailbox to collect them."), false);
    }

    static int open(ServerPlayerEntity player, int requestedPage) {
        try {
            List<Mail> mail = read(file(player));
            int page = Math.max(0, Math.min(requestedPage, Math.max(0, (mail.size() - 1) / PAGE_SIZE)));
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, owner) -> new MailboxScreen(syncId, player, mail, page),
                Text.literal("Mailbox - click to claim (" + (page + 1) + ")")));
            if (mail.isEmpty()) player.sendMessage(Text.literal("[Mailbox] Your mailbox is empty."), false);
            return 1;
        } catch (IOException failure) {
            error(player, failure);
            return 0;
        }
    }

    static boolean fits(ServerPlayerEntity player, ItemStack reward) {
        int remaining = reward.getCount();
        for (ItemStack slot : player.getInventory().main) {
            if (slot.isEmpty()) remaining -= Math.min(reward.getMaxCount(), player.getInventory().getMaxCountPerStack());
            else if (ItemStack.canCombine(slot, reward)) remaining -= Math.max(0,
                Math.min(slot.getMaxCount(), player.getInventory().getMaxCountPerStack()) - slot.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static void error(ServerPlayerEntity player, IOException failure) {
        LOGGER.error("Mailbox storage failed for {}", player.getUuid(), failure);
        player.sendMessage(Text.literal("[Mailbox] Mailbox storage failed. Please contact staff before trying again."), false);
    }

    private static final class MailboxScreen extends GenericContainerScreenHandler {
        private final ServerPlayerEntity owner;
        private final List<Mail> mail;
        private final int page;

        MailboxScreen(int syncId, ServerPlayerEntity owner, List<Mail> mail, int page) {
            super(net.minecraft.screen.ScreenHandlerType.GENERIC_9X6, syncId, owner.getInventory(),
                new SimpleInventory(54), 6);
            this.owner = owner;
            this.mail = mail;
            this.page = page;
            for (int slot = 0; slot < PAGE_SIZE && page * PAGE_SIZE + slot < mail.size(); slot++) {
                getInventory().setStack(slot, mail.get(page * PAGE_SIZE + slot).stack().copy());
            }
            if (page > 0) getInventory().setStack(45, new ItemStack(Items.ARROW).setCustomName(Text.literal("Previous page")));
            if ((page + 1) * PAGE_SIZE < mail.size()) {
                getInventory().setStack(53, new ItemStack(Items.ARROW).setCustomName(Text.literal("Next page")));
            }
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return player == owner;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
            if (player != owner || owner.currentScreenHandler != this) return;
            if ((slot >= 54 && slot < slots.size() && (action == SlotActionType.PICKUP
                    || action == SlotActionType.SWAP || action == SlotActionType.THROW))
                    || (slot == -999 && action == SlotActionType.PICKUP)) {
                super.onSlotClick(slot, button, action, player);
                return;
            }
            if (!getCursorStack().isEmpty()
                    || (action != SlotActionType.PICKUP && action != SlotActionType.QUICK_MOVE)) return;
            if (slot == 45 && page > 0) { open(owner, page - 1); return; }
            if (slot == 53 && (page + 1) * PAGE_SIZE < mail.size()) { open(owner, page + 1); return; }
            int index = page * PAGE_SIZE + slot;
            if (slot < 0 || slot >= PAGE_SIZE || index >= mail.size()) return;
            try {
                List<Mail> current = read(file(owner));
                Mail reward = current.stream().filter(entry -> entry.id().equals(mail.get(index).id())).findFirst().orElse(null);
                if (reward == null) { open(owner, page); return; }
                String receipt = "gangs_mailbox:" + reward.id();
                if (!owner.getCommandTags().contains(receipt)) {
                    if (!fits(owner, reward.stack())) {
                        owner.sendMessage(Text.literal("[Mailbox] Make room in your inventory first. The reward is still in your mailbox."), false);
                        return;
                    }
                    if (!owner.addCommandTag(receipt)) {
                        owner.sendMessage(Text.literal("[Mailbox] Could not create a delivery receipt. Contact staff; the reward is still in your mailbox."), false);
                        return;
                    }
                    owner.getInventory().insertStack(reward.stack().copy());
                    owner.getInventory().markDirty();
                }
                // Save item and receipt together before removing mail, so a crash cannot duplicate or lose it.
                ((PlayerManagerInvoker) owner.getServer().getPlayerManager())
                    .goldclaim$savePlayerData(owner);
                Path playerData = owner.getServer().getSavePath(WorldSavePath.PLAYERDATA)
                    .resolve(owner.getUuidAsString() + ".dat");
                NbtCompound saved = NbtIo.readCompressed(playerData.toFile());
                if (saved.getList("Tags", NbtElement.STRING_TYPE).stream()
                        .noneMatch(tag -> receipt.equals(tag.asString()))) {
                    throw new IOException("Player delivery receipt was not persisted");
                }
                current.remove(reward);
                write(file(owner), current);
                owner.getCommandTags().remove(receipt);
                owner.sendMessage(Text.literal("[Mailbox] Claimed ").append(reward.stack().toHoverableText()), false);
                open(owner, page);
            } catch (IOException failure) {
                error(owner, failure);
            }
        }
    }
}
