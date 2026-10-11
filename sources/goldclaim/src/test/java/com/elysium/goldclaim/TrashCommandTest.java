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
    public void publicTrashCommandIsRegisteredWithoutEssentialCommands() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        ServerCommandSource source = ordinarySource();
        TrashCommand.register(dispatcher);
        assertTrue(dispatcher.getRoot().getChild("trash").canUse(source));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("wastebin", source));
    }

    @Test
    public void commandRequiresAPlayerInsteadOfFailingOnAHandlerLookup() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        TrashCommand.register(dispatcher);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash", ordinarySource()));
    }

    @Test
    public void trashRejectsArgumentsAndHasNoSubcommands() {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        TrashCommand.register(dispatcher);
        assertTrue(dispatcher.getRoot().getChild("trash").getChildren().isEmpty());
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash admin", ordinarySource()));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("trash player", ordinarySource()));
    }
}
