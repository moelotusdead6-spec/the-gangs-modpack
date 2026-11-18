package com.gangs.invhistory;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class InvHistoryCommand {
    static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT).withZone(ZoneId.systemDefault());
    private static final SimpleCommandExceptionType UNKNOWN_PLAYER =
        new SimpleCommandExceptionType(Text.literal("Unknown player. Use an exact name or UUID."));
    private static final SimpleCommandExceptionType NOT_READY =
        new SimpleCommandExceptionType(Text.literal("Inventory history is not running."));

    private record Target(UUID uuid, String name) {}

    private InvHistoryCommand() {}

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var player = CommandManager.argument("player", StringArgumentType.word())
            .suggests((context, builder) -> CommandSource.suggestMatching(
                context.getSource().getServer().getPlayerNames(), builder));
        dispatcher.register(CommandManager.literal("invhistory")
            .requires(source -> Perms.check(source, Perms.VIEW, Perms.VIEW_LEVEL))
            .then(CommandManager.literal("list").then(player
                .executes(context -> list(context, 1))
                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> list(context, IntegerArgumentType.getInteger(context, "page"))))))
            .then(CommandManager.literal("view").then(CommandManager.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(
                    context.getSource().getServer().getPlayerNames(), builder))
                .then(CommandManager.argument("snapshot", LongArgumentType.longArg(0))
                    .executes(InvHistoryCommand::view)))));
    }

    private static int list(CommandContext<ServerCommandSource> context, int page) throws CommandSyntaxException {
        var source = context.getSource();
        var store = store();
        Target target = resolve(context);
        List<SnapshotStore.Entry> entries;
        try {
            entries = store.list(target.uuid());
        } catch (IOException failure) {
            InvHistoryMod.LOGGER.error("Cannot list inventory snapshots for {}.", target.uuid(), failure);
            source.sendError(Text.literal("Cannot read inventory history; see the server log."));
            return 0;
        }
        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No inventory snapshots for " + target.name()
                + " in the last " + store.retention().toDays() + " days."), false);
            return 0;
        }
        int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int shownPage = Math.min(page, pages);
        source.sendFeedback(() -> Text.literal("Inventory history for " + target.name() + " (" + entries.size()
            + " snapshots, kept " + store.retention().toDays() + " days) - page " + shownPage + "/" + pages)
            .formatted(Formatting.GOLD), false);
        long now = System.currentTimeMillis();
        int start = (shownPage - 1) * PAGE_SIZE;
        for (var entry : entries.subList(start, Math.min(entries.size(), start + PAGE_SIZE))) {
            source.sendFeedback(() -> line(target, entry, now), false);
        }
        MutableText navigation = Text.empty();
        if (shownPage > 1) navigation.append(link("[< Newer]", "/invhistory list " + target.name() + " " + (shownPage - 1)));
        if (shownPage < pages) {
            if (shownPage > 1) navigation.append(Text.literal("  "));
            navigation.append(link("[Older >]", "/invhistory list " + target.name() + " " + (shownPage + 1)));
        }
        if (pages > 1) source.sendFeedback(() -> navigation, false);
        return entries.size();
    }

    private static Text line(Target target, SnapshotStore.Entry entry, long now) {
        String location = "";
        String detail = "";
        try {
            NbtCompound data = net.minecraft.nbt.NbtIo.readCompressed(entry.file().toFile());
            location = data.getString("Dimension") + " " + position(data);
            detail = data.getString("Detail");
            if (entry.event() == SnapshotEvent.DEATH && !detail.isEmpty()) {
                Text cause = Text.Serializer.fromJson(detail);
                if (cause != null) detail = cause.getString();
            }
        } catch (IOException | RuntimeException failure) {
            location = "unreadable snapshot";
        }
        var hover = Text.literal(formatTime(entry.id()) + "\n" + location + (detail.isEmpty() ? "" : "\n" + detail)
            + "\nClick to open");
        return link("[View]", "/invhistory view " + target.name() + " " + entry.id())
            .append(Text.literal(" " + age(now - entry.id()) + " ").formatted(Formatting.GRAY))
            .append(Text.literal(entry.event().id()).formatted(color(entry.event())))
            .append(Text.literal(" " + location).formatted(Formatting.DARK_GRAY))
            .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static int view(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var source = context.getSource();
        var viewer = source.getPlayerOrThrow();
        Target target = resolve(context);
        long id = LongArgumentType.getLong(context, "snapshot");
        NbtCompound snapshot;
        try {
            snapshot = store().read(target.uuid(), id);
        } catch (IOException failure) {
            InvHistoryMod.LOGGER.error("Cannot read inventory snapshot {} for {}.", id, target.uuid(), failure);
            source.sendError(Text.literal("Cannot read that snapshot; see the server log."));
            return 0;
        }
        if (snapshot == null) {
            source.sendError(Text.literal("No snapshot " + id + " for " + target.name() + "."));
            return 0;
        }
        if (snapshot.getString("PlayerName").isEmpty()) snapshot.putString("PlayerName", target.name());
        SnapshotScreen.open(viewer, snapshot, Text.literal(target.name() + " - " + snapshot.getString("Event")
            + " - " + age(System.currentTimeMillis() - id)));
        InvHistoryMod.LOGGER.info("{} opened {}'s {} snapshot {}.", viewer.getGameProfile().getName(),
            target.name(), snapshot.getString("Event"), id);
        return 1;
    }

    private static SnapshotStore store() throws CommandSyntaxException {
        var store = InvHistoryMod.store();
        if (store == null) throw NOT_READY.create();
        return store;
    }

    private static Target resolve(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        var server = context.getSource().getServer();
        var online = server.getPlayerManager().getPlayer(name);
        if (online != null) return new Target(online.getUuid(), online.getGameProfile().getName());
        var cache = server.getUserCache();
        if (cache != null) {
            var cached = cache.findByName(name);
            if (cached.isPresent()) {
                GameProfile profile = cached.get();
                return new Target(profile.getId(), profile.getName());
            }
        }
        try {
            UUID uuid = UUID.fromString(name);
            return new Target(uuid, name);
        } catch (IllegalArgumentException notUuid) {
            throw UNKNOWN_PLAYER.create();
        }
    }

    static String formatTime(long millis) {
        return TIME.format(Instant.ofEpochMilli(millis));
    }

    static String position(NbtCompound snapshot) {
        return (int) Math.floor(snapshot.getDouble("X")) + " " + (int) Math.floor(snapshot.getDouble("Y")) + " "
            + (int) Math.floor(snapshot.getDouble("Z"));
    }

    static String age(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + minutes % 60 + "m ago";
        return hours / 24 + "d " + hours % 24 + "h ago";
    }

    private static MutableText link(String label, String command) {
        return Text.literal(label).styled(style -> style.withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static Formatting color(SnapshotEvent event) {
        return switch (event) {
            case JOIN -> Formatting.AQUA;
            case LEAVE -> Formatting.YELLOW;
            case DEATH -> Formatting.RED;
            case DIMENSION -> Formatting.LIGHT_PURPLE;
        };
    }
}
