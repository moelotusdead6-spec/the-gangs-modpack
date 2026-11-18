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
                String namedEmerald = "minecraft:emerald{display:{Name:'{\"text\":\"Stack fixture\"}'}}";
                require(dispatcher.execute("mailbox send MailFixture " + namedEmerald + " 130",
                    server.getCommandSource()) == 1, "Multi-stack send failed");
                var stacks = MailboxService.read(file);
                require(stacks.stream().map(entry -> entry.stack().getCount()).toList().equals(List.of(64, 64, 2)),
                    "Amount must split into full stacks and remainder");
                require(stacks.stream().allMatch(entry -> entry.stack().getName().getString().equals("Stack fixture")),
                    "Split stacks lost custom NBT");
                for (String invalid : List.of("0", "-1", "100001")) {
                    try {
                        dispatcher.execute("mailbox send MailFixture minecraft:emerald " + invalid, server.getCommandSource());
                        throw new IllegalStateException("Invalid amount was accepted: " + invalid);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                        require(MailboxService.read(file).size() == 3, "Invalid amount changed stored mail");
                    }
                }
                for (int i = 0; i < 36; i++) restored.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
                restored.getInventory().setStack(0, stacks.get(0).stack().copy());
                restored.getInventory().getStack(0).setCount(1);
                MailboxService.open(restored, 0);
                restored.currentScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
                require(MailboxService.read(file).size() == 3
                    && restored.getInventory().count(Items.EMERALD) == 1, "Insufficient partial space consumed a stack");
                restored.getInventory().setStack(1, ItemStack.EMPTY);
                var stackScreen = restored.currentScreenHandler;
                stackScreen.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, restored);
                require(MailboxService.read(file).size() == 2 && restored.getInventory().count(Items.EMERALD) == 65,
                    "Stack claim did not merge and deliver exact amount");
                var savedInventory = net.minecraft.nbt.NbtIo.readCompressed(server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA)
                    .resolve(uuid + ".dat").toFile()).getList("Inventory", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
                int savedEmeralds = 0;
                for (int i = 0; i < savedInventory.size(); i++) {
                    ItemStack savedStack = ItemStack.fromNbt(savedInventory.getCompound(i));
                    if (savedStack.isOf(Items.EMERALD)) savedEmeralds += savedStack.getCount();
                }
                require(savedEmeralds == 65, "Claimed stack was not persisted in player data");
                stackScreen.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
                require(restored.getInventory().count(Items.EMERALD) == 65, "Stale click duplicated a stack");
                MailboxService.write(file, List.of());
                require(dispatcher.execute("mailbox send MailFixture minecraft:ender_pearl 17", server.getCommandSource()) == 1,
                    "16-stack item send failed");
                require(dispatcher.execute("mailbox send MailFixture minecraft:diamond_sword 2", server.getCommandSource()) == 1,
                    "Unstackable item send failed");
                require(MailboxService.read(file).stream().map(entry -> entry.stack().getCount()).toList()
                    .equals(List.of(16, 1, 1, 1)), "Item-specific stack limits were not respected");
                MailboxService.write(file, List.of());
                require(dispatcher.execute("mailbox send MailFixture minecraft:stone 100000", server.getCommandSource()) == 1,
                    "Maximum allowed amount failed");
                require(MailboxService.read(file).stream().mapToInt(entry -> entry.stack().getCount()).sum() == 100000,
                    "Maximum amount did not persist exactly");
                var many = new java.util.ArrayList<MailboxService.Mail>();
                for (int i = 0; i < 91; i++) many.add(new MailboxService.Mail(UUID.randomUUID(), new ItemStack(Items.EMERALD)));
                MailboxService.write(file, many);
                MailboxService.open(restored, 0);
                require(!restored.currentScreenHandler.getSlot(45).hasStack(), "First page must not have a previous arrow");
                restored.currentScreenHandler.onSlotClick(53, 0, SlotActionType.PICKUP, restored);
                require(restored.currentScreenHandler.getSlot(0).getStack().isOf(Items.EMERALD), "Next page lost reward");
                require(restored.currentScreenHandler.getSlot(45).hasStack(), "Previous page missing");
                require(dispatcher.execute("mailbox page 3", restored.getCommandSource()) == 1, "Direct page command failed");
                require(restored.currentScreenHandler.getSlot(49).getStack().getName().getString().startsWith("Page 3/3"),
                    "Page indicator did not show current and total pages");
                require(!restored.currentScreenHandler.getSlot(1).hasStack()
                    && !restored.currentScreenHandler.getSlot(53).hasStack(), "Final page boundary is wrong");
                var lastPage = restored.currentScreenHandler;
                lastPage.onSlotClick(49, 0, SlotActionType.PICKUP, restored);
                require(MailboxService.read(file).size() == 91, "Page indicator was claimable");
                restored.getInventory().clear();
                lastPage.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
                require(MailboxService.read(file).size() == 90 && restored.getInventory().count(Items.EMERALD) == 1,
                    "Last page claim delivered wrong item count");
                require(restored.currentScreenHandler.getSlot(49).getStack().getName().getString().startsWith("Page 2/2"),
                    "Claiming the last entry must clamp to the remaining last page");
                lastPage.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
                require(restored.getInventory().count(Items.EMERALD) == 1, "Stale page duplicated reward");
                restored.currentScreenHandler.onSlotClick(45, 0, SlotActionType.PICKUP, restored);
                require(restored.currentScreenHandler.getSlot(49).getStack().getName().getString().startsWith("Page 1/2"),
                    "Previous page did not navigate back");
                require(dispatcher.execute("mailbox page 999", restored.getCommandSource()) == 1
                    && restored.currentScreenHandler.getSlot(49).getStack().getName().getString().startsWith("Page 2/2"),
                    "Out-of-range page must clamp to the last page");
                Files.writeString(file, "corrupt mailbox");
                require(MailboxService.open(restored, 0) == 0, "Corrupt storage must fail explicitly");
                require(Files.readString(file).equals("corrupt mailbox"), "Corrupt storage was overwritten");
                Files.delete(file);
                Path offlineFile = file.getParent().resolve(offlineUuid + ".json");
                Path blockedFile = file.getParent().resolve(blockedUuid + ".json");
                Files.createDirectory(blockedFile);
                int known = com.google.gson.JsonParser.parseString(Files.readString(Path.of("usercache.json"))).getAsJsonArray().size();
                require(dispatcher.execute("mailbox send all minecraft:emerald 65", server.getCommandSource()) == known - 1,
                    "Bulk send must return exact success count, excluding storage failure");
                require(MailboxService.read(file).stream().map(entry -> entry.stack().getCount()).toList().equals(List.of(64, 1)),
                    "all missed offline recipient stack amount");
                require(MailboxService.read(offlineFile).stream().map(entry -> entry.stack().getCount()).toList().equals(List.of(64, 1)),
                    "all missed second offline recipient stack amount");
                server.getPlayerManager().getPlayerList().add(restored);
                require(dispatcher.execute("mailbox send @a minecraft:diamond 32", server.getCommandSource()) == 1,
                    "@a must send to online players");
                server.getPlayerManager().getPlayerList().remove(restored);
                require(MailboxService.read(file).size() == 3 && MailboxService.read(file).get(2).stack().getCount() == 32,
                    "@a did not persist online stack amount");
                require(MailboxService.read(offlineFile).size() == 2, "@a must not target offline players");
                Files.delete(file);
                Files.delete(offlineFile);
                Files.delete(blockedFile);
                Files.writeString(Path.of("policy-smoke-passed.txt"), "PASS: mailbox public/admin permissions, offline neon send, "
                    + "durable stack storage, NBT, amount limits, item stack limits, merged claims, full inventory, blocked clicks, "
                    + "stale clicks, crash receipt recovery, numbered paging, final-page claims, corrupt data, "
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
