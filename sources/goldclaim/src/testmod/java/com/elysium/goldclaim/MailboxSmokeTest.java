package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class MailboxSmokeTest {
    private static boolean tested;

    static void register() {
        Identifier neon = new Identifier("neon-wall-wonders-gwbzt3mk", "neon_arcade_alien");
        if (!Registries.ITEM.containsId(neon)) Registry.register(Registries.ITEM, neon, new Item(new Item.Settings()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (tested || server.getTicks() < 30) return;
            tested = true;
            try {
                var api = LuckPermsProvider.get();
                UUID uuid = UUID.randomUUID();
                UUID offlineUuid = UUID.randomUUID();
                UUID blockedUuid = UUID.randomUUID();
                GameProfile profile = new GameProfile(uuid, "MailFixture");
                server.getUserCache().add(profile);
                server.getUserCache().add(new GameProfile(offlineUuid, "MailOffline"));
                server.getUserCache().add(new GameProfile(blockedUuid, "MailBlocked"));
                server.getUserCache().save();
                var user = api.getUserManager().loadUser(uuid).join();
                user.data().clear();
                var grant = Node.builder("goldclaim.mailbox.send").build();
                var player = new ServerPlayerEntity(server, server.getOverworld(), profile);
                player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), player);
                var dispatcher = server.getCommandManager().getDispatcher();
                var root = dispatcher.getRoot().getChild("mailbox");
                require(root.canUse(player.getCommandSource()), "Mailbox must be public");
                require(!root.getChild("send").canUse(player.getCommandSource()), "Ordinary players cannot send");
                user.data().add(grant);
                user.getCachedData().invalidate();
                require(MailboxService.canSend(player.getCommandSource()), "Explicit grant must permit sending");
                user.data().remove(grant);
                user.getCachedData().invalidate();
                var suggestions = dispatcher.getCompletionSuggestions(
                    dispatcher.parse("mailbox send MailF", server.getCommandSource())).join();
                require(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("MailFixture")),
                    "Offline username autocomplete missing");
                var bulkSuggestions = dispatcher.getCompletionSuggestions(
                    dispatcher.parse("mailbox send ", server.getCommandSource())).join();
                require(bulkSuggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("@a")),
                    "@a autocomplete missing");
                require(bulkSuggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("all")),
                    "all autocomplete missing");
                for (String rank : List.of("mod", "admin", "owner")) {
                    var group = api.getGroupManager().createAndLoadGroup(rank).join();
                    var inheritance = InheritanceNode.builder(group.getName()).build();
                    user.data().add(inheritance);
                    user.getCachedData().invalidate();
                    require(MailboxService.canSend(player.getCommandSource()), rank + " cannot send");
                    var deny = Node.builder("goldclaim.mailbox.send").value(false).build();
                    user.data().add(deny);
                    user.getCachedData().invalidate();
                    require(!MailboxService.canSend(player.getCommandSource()), "Explicit deny must win");
                    user.data().remove(deny);
                    user.data().remove(inheritance);
                    user.getCachedData().invalidate();
                }
                require(server.getPlayerManager().getPlayer(uuid) == null, "Recipient must be offline");
                require(dispatcher.execute("mailbox send MailFixture " + neon, server.getCommandSource()) == 1,
                    "Offline send failed");
                Path file = MailboxService.file(player);
                require(MailboxService.read(file).size() == 1, "Mail did not persist");
                require(MailboxService.read(file).get(0).stack().isOf(Registries.ITEM.get(neon)), "Wrong giveaway item");
                require(dispatcher.execute("mailbox", player.getCommandSource()) == 1, "Menu did not open");
                var stale = player.currentScreenHandler;
                stale.onSlotClick(0, 0, SlotActionType.CLONE, player);
                stale.onSlotClick(0, 0, SlotActionType.THROW, player);
                stale.onSlotClick(0, 0, SlotActionType.SWAP, player);
                stale.onSlotClick(0, 0, SlotActionType.PICKUP_ALL, player);
                require(MailboxService.read(file).size() == 1, "Unsupported clicks consumed mail");
                for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
                stale.onSlotClick(54, 0, SlotActionType.PICKUP, player);
                require(stale.getCursorStack().isOf(Items.STONE), "Player inventory must remain usable");
                stale.onSlotClick(0, 0, SlotActionType.PICKUP, player);
                require(MailboxService.read(file).size() == 1, "Cursor item must not replace reward");
                stale.onSlotClick(54, 0, SlotActionType.PICKUP, player);
                stale.onSlotClick(0, 0, SlotActionType.PICKUP, player);
                require(MailboxService.read(file).size() == 1, "Full inventory lost reward");
                player.getInventory().setStack(0, ItemStack.EMPTY);
                stale.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, player);
                require(player.getInventory().getStack(0).isOf(Registries.ITEM.get(neon)), "Claim did not deliver item");
                require(MailboxService.read(file).isEmpty(), "Claim did not remove mail");
                stale.onSlotClick(0, 0, SlotActionType.PICKUP, player);
                require(player.getInventory().getStack(0).getCount() == 1, "Stale click duplicated reward");
                // Simulate a crash after player save but before mailbox removal.
                var recovery = new MailboxService.Mail(UUID.randomUUID(),
                    new ItemStack(Items.DIAMOND).setCustomName(Text.literal("Receipt fixture")));
                MailboxService.write(file, List.of(recovery));
                String receipt = "gangs_mailbox:" + recovery.id();
                player.addCommandTag(receipt);
                player.getInventory().setStack(1, recovery.stack().copy());
                ((com.elysium.goldclaim.mixin.PlayerManagerInvoker) server.getPlayerManager()).goldclaim$savePlayerData(player);
                var restored = new ServerPlayerEntity(server, server.getOverworld(), profile);
                restored.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND), restored);
                restored.readNbt(net.minecraft.nbt.NbtIo.readCompressed(server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA)
                    .resolve(uuid + ".dat").toFile()));
                MailboxService.open(restored, 0);
                restored.currentScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
                require(restored.getInventory().count(Items.DIAMOND) == 1, "Crash recovery duplicated delivery");
                require(MailboxService.read(file).isEmpty(), "Receipt recovery did not finish");
                var many = new java.util.ArrayList<MailboxService.Mail>();
                for (int i = 0; i < 46; i++) many.add(new MailboxService.Mail(UUID.randomUUID(), new ItemStack(Items.EMERALD)));
                MailboxService.write(file, many);
                MailboxService.open(restored, 0);
                restored.currentScreenHandler.onSlotClick(53, 0, SlotActionType.PICKUP, restored);
                require(restored.currentScreenHandler.getSlot(0).getStack().isOf(Items.EMERALD), "Next page lost reward");
                require(restored.currentScreenHandler.getSlot(45).hasStack(), "Previous page missing");
                Files.writeString(file, "corrupt mailbox");
                require(MailboxService.open(restored, 0) == 0, "Corrupt storage must fail explicitly");
                require(Files.readString(file).equals("corrupt mailbox"), "Corrupt storage was overwritten");
                Files.delete(file);
                Path offlineFile = file.getParent().resolve(offlineUuid + ".json");
                Path blockedFile = file.getParent().resolve(blockedUuid + ".json");
                Files.createDirectory(blockedFile);
                int known = com.google.gson.JsonParser.parseString(Files.readString(Path.of("usercache.json"))).getAsJsonArray().size();
                require(dispatcher.execute("mailbox send all minecraft:emerald", server.getCommandSource()) == known - 1,
                    "Bulk send must return exact success count, excluding storage failure");
                require(MailboxService.read(file).size() == 1, "all missed offline recipient");
                require(MailboxService.read(offlineFile).size() == 1, "all missed second offline recipient");
                server.getPlayerManager().getPlayerList().add(restored);
                require(dispatcher.execute("mailbox send @a minecraft:diamond", server.getCommandSource()) == 1,
                    "@a must send to online players");
                server.getPlayerManager().getPlayerList().remove(restored);
                require(MailboxService.read(file).size() == 2, "@a did not persist online mail");
                require(MailboxService.read(offlineFile).size() == 1, "@a must not target offline players");
                Files.delete(file);
                Files.delete(offlineFile);
                Files.delete(blockedFile);
                Files.writeString(Path.of("policy-smoke-passed.txt"), "PASS: mailbox public/admin permissions, offline neon send, "
                    + "durable storage, menu claims, full inventory, blocked clicks, stale clicks, crash receipt recovery, paging, corrupt data, "
                    + "offline autocomplete, all known recipients and @a online recipients.\n");
                server.stop(false);
            } catch (Exception failure) {
                throw new IllegalStateException("Mailbox smoke failed", failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
