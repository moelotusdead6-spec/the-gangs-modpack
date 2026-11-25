package com.gangs.gangsgems;

import java.io.IOException;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.server.MinecraftServer;

final class StaffPermissionsSmoke {
    private StaffPermissionsSmoke() {}

    static void run(MinecraftServer server, GemsSmoke.FixturePlayer target, WalletStore wallet) throws IOException {
        var api = LuckPermsProvider.get();
        api.getGroupManager().createAndLoadGroup("mod").join();
        api.getGroupManager().createAndLoadGroup("admin").join();
        var moderator = new GemsSmoke.FixturePlayer(server, server.getOverworld());
        var admin = new GemsSmoke.FixturePlayer(server, server.getOverworld());
        var ordinary = new GemsSmoke.FixturePlayer(server, server.getOverworld());
        var modUser = api.getUserManager().loadUser(moderator.getUuid()).join();
        var adminUser = api.getUserManager().loadUser(admin.getUuid()).join();
        api.getUserManager().loadUser(ordinary.getUuid()).join();
        modUser.data().add(InheritanceNode.builder("mod").build());
        adminUser.data().add(InheritanceNode.builder("admin").build());
        long balance = wallet.balance(target.getUuid());
        var commands = server.getCommandManager();
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems grant GemsFixture 25 event prize") == 1,
            "Moderator could not grant Gems");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems remove GemsFixture 5 correction") == 0,
            "Moderator could remove Gems");
        require(commands.executeWithPrefix(admin.getCommandSource(), "ganggems remove GemsFixture 5 correction") == 1,
            "Admin could not remove Gems");
        require(commands.executeWithPrefix(ordinary.getCommandSource().withLevel(4),
            "ganggems grant GemsFixture 25 unauthorized") == 0, "Non-staff could grant Gems");
        require(commands.executeWithPrefix(ordinary.getCommandSource().withLevel(4),
            "ganggems remove GemsFixture 5 unauthorized") == 0, "Non-admin could remove Gems");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems balance GemsFixture") == 1,
            "Moderator could not check a balance");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "gb GemsFixture") == 1,
            "Moderator could not check a balance using the alias");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems status") == 1,
            "Moderator could not inspect bridge status");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems audit") == 1,
            "Moderator could not inspect the audit");
        String pending = "staff-pending-" + target.getUuid();
        wallet.recordPaymentFor(pending, null, 100);
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems pending") == 1,
            "Moderator could not inspect pending orders");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems transaction " + pending) == 1,
            "Moderator could not inspect a transaction");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems assign " + pending + " GemsFixture") == 1,
            "Moderator could not assign a pending order");
        require(commands.executeWithPrefix(moderator.getCommandSource(), "ganggems history GemsFixture") == 1,
            "Moderator could not inspect purchase history");
        require(wallet.balance(target.getUuid()) == balance + 120, "Staff actions changed the wrong balance");
        require(wallet.audit(target.getUuid()).stream().filter(a -> a.action().equals("grant")
            || a.action().equals("remove")).count() == 2, "Staff audit entries missing");
        System.out.println("GANGS_GEMS_STAFF_SMOKE_PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
