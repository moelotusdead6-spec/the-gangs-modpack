package com.elysium.goldclaim;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

final class TrashCommand {
    private static final int SLOT_COUNT = 27;
    private static final Text TITLE = Text.literal("Trash");

    private TrashCommand() {
    }

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("trash").executes(context -> {
            context.getSource().getPlayerOrThrow().openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, player) -> GenericContainerScreenHandler.createGeneric9x3(
                    syncId, playerInventory, new SimpleInventory(SLOT_COUNT)),
                TITLE));
            return 1;
        }));
    }
}
