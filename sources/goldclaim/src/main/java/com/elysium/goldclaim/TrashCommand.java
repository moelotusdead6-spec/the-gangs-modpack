package com.elysium.goldclaim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

final class TrashCommand {
    private TrashCommand() {
    }

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("trash").executes(context -> {
            CommandNode<ServerCommandSource> wastebin = dispatcher.getRoot().getChild("wastebin");
            if (wastebin == null || wastebin.getCommand() == null) {
                throw new SimpleCommandExceptionType(Text.literal(
                    "Trash is unavailable: Essential Commands /wastebin must be enabled on this server.")).create();
            }
            // Reuse only the bin handler, without granting permissions or elevating the player's source.
            return wastebin.getCommand().run(context);
        }));
    }
}
