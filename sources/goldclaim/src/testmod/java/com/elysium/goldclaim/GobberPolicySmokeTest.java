package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

final class GobberPolicySmokeTest {
    private GobberPolicySmokeTest() {}

    static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (String suffix : new String[] {"", "_nether", "_end"}) {
                ItemStack ingot = stack("gobber2_ingot" + suffix);
                for (String tool : new String[] {"pickaxe", "axe", "tree_axe", "hoe", "shovel", "hammer", "excavator", "paxel"}) {
                    verifyTool("gobber2_" + tool + suffix, ingot, suffix.equals("_end"));
                }
            }
            verifyTool("gobber2_paxel_stars", stack("gobber2_ingot_end"), true);
            for (String suffix : new String[] {"", "_nether", "_end"}) verifyActualRepairs(server, suffix);
            for (String item : new String[] {"gobber2_sword", "gobber2_sword_sniper", "gobber2_bow_end",
                    "gobber2_arrow", "gobber2_staff_sniper", "gobber2_chestplate_dragon"}) {
                require(GobberPolicy.isRemoved("gobber2:" + item), "Not removed: " + item);
                require(server.getRecipeManager().get(new Identifier("gobber2", item)).isEmpty(),
                        "Removed item recipe remains: " + item);
            }
            ItemStack medallion = stack("gobber2_medallion_suffering");
            require(!GobberPolicy.sanitize(medallion) && !medallion.isEmpty(), "Medallion changed");
            SimpleInventory chest = new SimpleInventory(1);
            chest.setStack(0, stack("gobber2_sword"));
            try {
                verifyRecovery(server, chest, medallion);
                Files.writeString(Path.of("gobber-policy-smoke-passed.txt"), "PASS\n");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Gobber recovery smoke failed", failure);
            }
            server.stop(false);
        });
    }

    private static void verifyActualRepairs(MinecraftServer server, String suffix) {
        ServerPlayerEntity player = player(server, UUID.randomUUID());
        ItemStack tool = stack("gobber2_pickaxe" + suffix);
        tool.setDamage(100);
        var anvil = new AnvilScreenHandler(0, player.getInventory(),
            ScreenHandlerContext.create(server.getOverworld(), BlockPos.ORIGIN));
        anvil.getSlot(0).setStack(tool.copy());
        anvil.getSlot(1).setStack(stack("gobber2_ingot" + suffix));
        anvil.updateResult();
        ItemStack repaired = anvil.getSlot(2).getStack();
        require(!repaired.isEmpty() && repaired.getDamage() < tool.getDamage(),
            "Anvil did not repair tier " + suffix);
        tool.addEnchantment(Enchantments.MENDING, 1);
        player.equipStack(EquipmentSlot.MAINHAND, tool);
        new ExperienceOrbEntity(server.getOverworld(), 0, 64, 0, 5).onPlayerCollision(player);
        require(tool.getDamage() < 100, "Mending XP did not repair tier " + suffix);
    }

    private static void verifyRecovery(MinecraftServer server, SimpleInventory chest, ItemStack medallion)
            throws java.io.IOException {
        ServerPlayerEntity player = player(server, UUID.randomUUID());
        for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
        ItemStack sword = chest.getStack(0).copy();
        sword.addEnchantment(Enchantments.SHARPNESS, 5);
        sword.addEnchantment(Enchantments.VANISHING_CURSE, 1);
        player.getInventory().setStack(0, sword);
        player.getInventory().setStack(1, medallion);
        ItemStack bow = stack("gobber2_bow_end");
        bow.addEnchantment(Enchantments.POWER, 4);
        player.currentScreenHandler.setCursorStack(bow);
        GobberPolicy.process(player);
        require(player.getInventory().getStack(0).isEmpty(), "Withdrawn sword remains");
        require(player.currentScreenHandler.getCursorStack().isEmpty(), "Cursor bow remains");
        require(!player.getInventory().getStack(1).isEmpty(), "Medallion was removed");
        require(!chest.getStack(0).isEmpty(), "Stored sword was removed before withdrawal");
        List<MailboxService.Mail> mail = MailboxService.read(MailboxService.file(player));
        require(mail.size() == 3, "Expected separate books for all enchantments and curses");
        require(mail.stream().allMatch(entry -> entry.stack().isOf(Items.ENCHANTED_BOOK)
            && entry.stack().getNbt().getList("StoredEnchantments", NbtElement.COMPOUND_TYPE).size() == 1),
            "Recovery did not create one enchantment per book");
        var enchantments = mail.stream().map(entry -> entry.stack().getNbt()
            .getList("StoredEnchantments", NbtElement.COMPOUND_TYPE).getCompound(0)).toList();
        require(enchantments.stream().anyMatch(entry -> entry.getString("id").equals("minecraft:sharpness")
            && entry.getShort("lvl") == 5), "Sharpness level was not preserved");
        require(enchantments.stream().anyMatch(entry -> entry.getString("id").equals("minecraft:vanishing_curse")),
            "Curse was not preserved");
        require(enchantments.stream().anyMatch(entry -> entry.getString("id").equals("minecraft:power")
            && entry.getShort("lvl") == 4), "Cursor enchantment was not preserved");
        GobberPolicy.process(player);
        require(MailboxService.read(MailboxService.file(player)).size() == 3, "Recovery duplicated books");

        MailboxService.write(GobberPolicy.journal(player), List.of(mail.get(0)));
        player.addCommandTag("gangs_gobber:" + mail.get(0).id());
        ((com.elysium.goldclaim.mixin.PlayerManagerInvoker) server.getPlayerManager()).goldclaim$savePlayerData(player);
        ServerPlayerEntity restored = player(server, player.getUuid());
        restored.readNbt(NbtIo.readCompressed(server.getSavePath(WorldSavePath.PLAYERDATA)
            .resolve(player.getUuidAsString() + ".dat").toFile()));
        GobberPolicy.finishPending(restored);
        require(MailboxService.read(MailboxService.file(restored)).size() == 3,
            "Interrupted recovery duplicated a book");
        require(!Files.exists(GobberPolicy.journal(restored)), "Interrupted recovery journal remains");

        MailboxService.write(GobberPolicy.journal(restored), List.of(mail.get(0)));
        GobberPolicy.finishPending(restored);
        require(MailboxService.read(MailboxService.file(restored)).size() == 3,
            "Uncommitted recovery changed mailbox");

        MailboxService.write(GobberPolicy.journal(restored), List.of(mail.get(0)));
        restored.addCommandTag("gangs_gobber:" + mail.get(0).id());
        restored.getInventory().setStack(2, ItemStack.EMPTY);
        MailboxService.open(restored, 0);
        restored.currentScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
        require(MailboxService.read(MailboxService.file(restored)).size() == 3
            && restored.getInventory().getStack(2).isEmpty(), "Pending recovery book was claimable");
        GobberPolicy.finishPending(restored);
        restored.currentScreenHandler.onSlotClick(0, 0, SlotActionType.PICKUP, restored);
        require(MailboxService.read(MailboxService.file(restored)).size() == 2,
            "Completed recovery book was not claimable");

        ServerPlayerEntity blocked = player(server, UUID.randomUUID());
        ItemStack held = stack("gobber2_sword");
        held.addEnchantment(Enchantments.SHARPNESS, 3);
        blocked.getInventory().setStack(0, held);
        Files.createDirectories(MailboxService.file(blocked));
        try {
            GobberPolicy.process(blocked);
            throw new IllegalStateException("Unavailable mailbox was accepted");
        } catch (java.io.IOException expected) {
            require(!blocked.getInventory().getStack(0).isEmpty(), "Mailbox failure destroyed equipment");
        }
    }

    private static ServerPlayerEntity player(MinecraftServer server, UUID id) {
        var player = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(id, "GobberFixture"));
        player.networkHandler = new ServerPlayNetworkHandler(server,
            new ClientConnection(NetworkSide.SERVERBOUND), player);
        return player;
    }

    private static void verifyTool(String name, ItemStack ingot, boolean wasUnbreakable) {
        ItemStack tool = stack(name);
        if (wasUnbreakable) {
            tool.getOrCreateNbt().putBoolean("Unbreakable", true);
            tool.getOrCreateNbt().putString("custom", "preserve");
            require(GobberPolicy.sanitize(tool), "Unbreakable flag was not removed: " + name);
            require(tool.getNbt().getString("custom").equals("preserve"), "Other NBT was lost: " + name);
        }
        require(tool.isDamageable(), "Tool is not damageable: " + name);
        require(tool.getItem().canRepair(tool, ingot), "Ingot does not repair: " + name);
        require(Enchantments.MENDING.isAcceptableItem(tool), "Mending is not applicable: " + name);
        tool.damage(1, Random.create(), null);
        require(tool.getDamage() == 1, "Tool did not take damage: " + name);
    }

    private static ItemStack stack(String name) {
        Identifier id = new Identifier("gobber2", name);
        require(Registries.ITEM.containsId(id), "Missing item: " + id);
        return new ItemStack(Registries.ITEM.get(id));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
