package com.elysium.goldclaim;

import com.elysium.goldclaim.mixin.PlayerManagerInvoker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GobberPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("GoldClaim/GobberPolicy");
    private static final Map<UUID, Long> RETRY_AFTER = new HashMap<>();
    private static final String RECEIPT_PREFIX = "gangs_gobber:";
    private static final Set<String> REMOVED = Set.of(
        "gobber2_sword", "gobber2_sword_nether", "gobber2_sword_end", "gobber2_sword_sniper",
        "gobber2_bow", "gobber2_bow_nether", "gobber2_bow_end",
        "gobber2_arrow", "gobber2_arrow_nether", "gobber2_arrow_end", "gobber2_staff_sniper",
        "gobber2_helmet", "gobber2_chestplate", "gobber2_leggings", "gobber2_boots",
        "gobber2_helmet_nether", "gobber2_chestplate_nether", "gobber2_leggings_nether", "gobber2_boots_nether",
        "gobber2_helmet_end", "gobber2_chestplate_end", "gobber2_leggings_end", "gobber2_boots_end",
        "gobber2_helmet_dragon", "gobber2_chestplate_dragon", "gobber2_leggings_dragon", "gobber2_boots_dragon"
    );
    private static final Set<String> END_TOOLS = Set.of(
        "gobber2_pickaxe_end", "gobber2_hammer_end", "gobber2_excavator_end", "gobber2_paxel_end",
        "gobber2_paxel_stars", "gobber2_axe_end", "gobber2_tree_axe_end", "gobber2_hoe_end",
        "gobber2_shovel_end"
    );

    private GobberPolicy() {}

    static boolean isRemoved(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed != null && parsed.getNamespace().equals("gobber2") && REMOVED.contains(parsed.getPath());
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (server.getTicks() < RETRY_AFTER.getOrDefault(player.getUuid(), 0L)) continue;
                try {
                    process(player);
                    RETRY_AFTER.remove(player.getUuid());
                } catch (IOException failure) {
                    RETRY_AFTER.put(player.getUuid(), (long) server.getTicks() + 100);
                    LOGGER.error("Could not recover Gobber enchantments for {}", player.getUuid(), failure);
                    player.sendMessage(Text.literal("[Gobber] Enchantment recovery could not be saved. "
                        + "Please contact staff; recovery will be retried."), false);
                }
            }
        });
    }

    static void process(ServerPlayerEntity player) throws IOException {
        finishPending(player);
        Inventory inventory = player.getInventory();
        List<ItemStack> removed = new ArrayList<>();
        List<MailboxService.Mail> books = new ArrayList<>();
        boolean changed = false;
        for (int slot = 0; slot < inventory.size(); slot++) {
            changed |= collect(inventory.getStack(slot), removed, books);
        }
        changed |= collect(player.currentScreenHandler.getCursorStack(), removed, books);
        if (!books.isEmpty()) {
            MailboxService.read(MailboxService.file(player));
            Path journal = journal(player);
            MailboxService.write(journal, books);
            String receipt = receipt(books);
            if (!player.addCommandTag(receipt)) {
                Files.delete(journal);
                throw new IOException("Cannot create Gobber recovery receipt");
            }
        }
        for (ItemStack stack : removed) stack.setCount(0);
        if (changed || !removed.isEmpty()) {
            inventory.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        }
        if (!books.isEmpty()) finishPending(player);
    }

    private static boolean collect(ItemStack stack, List<ItemStack> removed, List<MailboxService.Mail> books)
            throws IOException {
        if (stack.isEmpty()) return false;
        if (!isRemoved(Registries.ITEM.getId(stack.getItem()).toString())) return sanitize(stack);
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains("Enchantments")) {
            if (!(nbt.get("Enchantments") instanceof NbtList enchantments)) {
                throw new IOException("Invalid enchantment list on removed Gobber equipment");
            }
            for (NbtElement enchantment : enchantments) {
                if (!(enchantment instanceof NbtCompound entry)) {
                    throw new IOException("Invalid enchantment on removed Gobber equipment");
                }
                for (int count = 0; count < stack.getCount(); count++) {
                    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                    NbtList stored = new NbtList();
                    stored.add(entry.copy());
                    book.getOrCreateNbt().put("StoredEnchantments", stored);
                    books.add(new MailboxService.Mail(UUID.randomUUID(), book));
                }
            }
        }
        removed.add(stack);
        return false;
    }

    static Path journal(ServerPlayerEntity player) {
        return player.getServer().getSavePath(WorldSavePath.ROOT)
            .resolve("gangs-gobber-recovery").resolve(player.getUuidAsString() + ".json");
    }

    static boolean isPendingBook(ServerPlayerEntity player, UUID id) throws IOException {
        Path journal = journal(player);
        return Files.exists(journal) && MailboxService.read(journal).stream().anyMatch(book -> book.id().equals(id));
    }

    private static String receipt(List<MailboxService.Mail> books) throws IOException {
        if (books.isEmpty()) throw new IOException("Empty Gobber recovery journal");
        return RECEIPT_PREFIX + books.get(0).id();
    }

    static void finishPending(ServerPlayerEntity player) throws IOException {
        Path journal = journal(player);
        if (!Files.exists(journal)) return;
        List<MailboxService.Mail> books = MailboxService.read(journal);
        String receipt = receipt(books);
        if (!player.getCommandTags().contains(receipt)) {
            Files.delete(journal);
            return;
        }
        // The journal precedes the player save; a persisted receipt proves the removal was saved.
        ((PlayerManagerInvoker) player.getServer().getPlayerManager()).goldclaim$savePlayerData(player);
        Path playerData = player.getServer().getSavePath(WorldSavePath.PLAYERDATA)
            .resolve(player.getUuidAsString() + ".dat");
        NbtCompound saved = NbtIo.readCompressed(playerData.toFile());
        if (saved.getList("Tags", NbtElement.STRING_TYPE).stream()
                .noneMatch(tag -> receipt.equals(tag.asString()))) {
            throw new IOException("Gobber recovery receipt was not persisted");
        }
        Path mailbox = MailboxService.file(player);
        List<MailboxService.Mail> mail = MailboxService.read(mailbox);
        Set<UUID> existing = new HashSet<>();
        for (MailboxService.Mail entry : mail) existing.add(entry.id());
        for (MailboxService.Mail book : books) {
            if (existing.add(book.id())) mail.add(book);
        }
        MailboxService.write(mailbox, mail);
        Files.delete(journal);
        player.getCommandTags().remove(receipt);
        player.sendMessage(Text.literal("[Gobber] Your recovered enchantments are in /mailbox."), false);
    }

    static boolean sanitize(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        NbtCompound nbt = stack.getNbt();
        if (id.getNamespace().equals("gobber2") && END_TOOLS.contains(id.getPath())
                && nbt != null && nbt.contains("Unbreakable")) {
            nbt.remove("Unbreakable");
            return true;
        }
        return false;
    }
}
