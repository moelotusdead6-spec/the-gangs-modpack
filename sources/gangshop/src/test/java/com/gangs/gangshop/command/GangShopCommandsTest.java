package com.gangs.gangshop.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class GangShopCommandsTest {
    @Test
    public void obsoleteCategoryCommandsAreNotRegistered() throws Exception {
        var dispatcher = new CommandDispatcher<ServerCommandSource>();
        var register = GangShopCommands.class.getDeclaredMethod("register", CommandDispatcher.class);
        register.setAccessible(true);
        register.invoke(null, dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("shop"));
        assertNull(dispatcher.getRoot().getChild("shopfoods"));
        assertNull(dispatcher.getRoot().getChild("shopmetals"));
        assertNull(dispatcher.getRoot().getChild("shopfoodsbuy"));
        assertNull(dispatcher.getRoot().getChild("shopmetalsbuy"));
    }
}
