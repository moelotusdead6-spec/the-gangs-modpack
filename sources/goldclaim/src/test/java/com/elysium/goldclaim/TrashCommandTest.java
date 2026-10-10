package com.elysium.goldclaim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrashCommandTest {
    private static ServerCommandSource ordinarySource() {
        return new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO,
            null, 0, "Test", Text.literal("Test"), null, null);
    }

    @Test
    public void publicAliasUsesExistingHandlerWithoutElevatingSourceOrUnlockingOriginal() throws Exception {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        ServerCommandSource source = ordinarySource();
        dispatcher.register(CommandManager.literal("wastebin").requires(s -> s.hasPermissionLevel(2))
            .executes(context -> {
                assertSame(source, context.getSource());
                assertFalse(context.getSource().hasPermissionLevel(1));
                return 7;
            }));
        TrashCommand.register(dispatcher);
        assertTrue(dispatcher.getRoot().getChild("trash").canUse(source));
        assertEquals(7, dispatcher.execute("trash", source));
        assertFalse(dispatcher.getRoot().getChild("wastebin").canUse(source));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("wastebin", source));
    }

    @Test
    public void handlerCanRegisterAfterAliasAndBeReplacedWithoutStaleReferences() throws Exception {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        TrashCommand.register(dispatcher);
        dispatcher.register(CommandManager.literal("wastebin").executes(context -> 1));
        assertEquals(1, dispatcher.execute("trash", ordinarySource()));
        dispatcher.register(CommandManager.literal("wastebin").executes(context -> 2));
        assertEquals(2, dispatcher.execute("trash", ordinarySource()));
    }

    @Test
    public void missingOrDisabledWastebinReportsActionableError() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        TrashCommand.register(dispatcher);
        CommandSyntaxException missing = assertThrows(CommandSyntaxException.class,
            () -> dispatcher.execute("trash", ordinarySource()));
        assertTrue(missing.getMessage().contains("/wastebin must be enabled"));
        dispatcher.register(CommandManager.literal("wastebin"));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash", ordinarySource()));
    }

    @Test
    public void consoleRejectionFromOriginalHandlerIsPreserved() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(CommandManager.literal("wastebin").executes(context -> {
            context.getSource().getPlayerOrThrow();
            return 1;
        }));
        TrashCommand.register(dispatcher);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash", ordinarySource()));
    }

    @Test
    public void aliasRejectsArgumentsAndDoesNotExposeRestrictedSubcommands() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(CommandManager.literal("wastebin").executes(context -> 1)
            .then(CommandManager.literal("admin").requires(s -> s.hasPermissionLevel(2))
                .executes(context -> 2)));
        TrashCommand.register(dispatcher);
        assertTrue(dispatcher.getRoot().getChild("trash").getChildren().isEmpty());
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash admin", ordinarySource()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash player", ordinarySource()));
    }
}
