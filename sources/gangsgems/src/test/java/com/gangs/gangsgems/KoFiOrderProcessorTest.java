package com.gangs.gangsgems;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class KoFiOrderProcessorTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void verifiesAndDeduplicatesShopOrders() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        UUID player = UUID.randomUUID();
        String code = wallet.createLink(player, 1000);
        KoFiOrderProcessor processor = processor(wallet);
        JsonObject event = event(code);
        assertEquals(KoFiOrderProcessor.Result.APPLIED, processor.process(form(event), 1001));
        assertEquals(KoFiOrderProcessor.Result.DUPLICATE, processor.process(form(event), 1002));
        assertEquals(200, wallet.balance(player));
        assertEquals(200, new WalletStore(folder.getRoot().toPath().resolve("wallet.json")).unannounced(player));
    }

    @Test
    public void rejectsSpoofingUnderpaymentUnknownProductsAndDuplicateFormData() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        KoFiOrderProcessor processor = processor(wallet);
        JsonObject event = event(null);
        event.addProperty("verification_token", "wrong");
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(event), 1));
        event.addProperty("verification_token", "secret");
        event.addProperty("amount", "3.99");
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(event), 1));
        event.addProperty("amount", "4.00");
        event.getAsJsonArray("shop_items").get(0).getAsJsonObject().addProperty("variation_name", "unknown");
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(event), 1));
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(event) + "&data=%7B%7D", 1));
        assertFalse(java.nio.file.Files.exists(folder.getRoot().toPath().resolve("wallet.json")));
    }

    @Test
    public void unmatchedOrdersArePersistedForManualAssignment() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        KoFiOrderProcessor processor = processor(wallet);
        assertEquals(KoFiOrderProcessor.Result.PENDING, processor.process(form(event(null)), 1));
        UUID player = UUID.randomUUID();
        new WalletStore(folder.getRoot().toPath().resolve("wallet.json")).assignPending("order-1", player);
        assertEquals(200, new WalletStore(folder.getRoot().toPath().resolve("wallet.json")).balance(player));
    }

    @Test
    public void usernameOrderCreditsRecipientOnceAndRejectsConflictingReplay() throws Exception {
        var path = folder.getRoot().toPath().resolve("wallet.json");
        WalletStore wallet = new WalletStore(path);
        KoFiOrderProcessor processor = processor(wallet);
        UUID player = UUID.randomUUID();
        JsonObject event = event(" GiftPlayer ");
        assertEquals(KoFiOrderProcessor.Result.APPLIED, processor.process(form(event), 1, name -> {
            assertEquals("GiftPlayer", name);
            return player;
        }));
        assertEquals(KoFiOrderProcessor.Result.DUPLICATE, processor.process(form(event), 2, name -> player));
        assertEquals(200, new WalletStore(path).balance(player));
        assertThrows(IllegalStateException.class, () -> processor.process(form(event), 3, name -> UUID.randomUUID()));
        assertEquals(200, new WalletStore(path).balance(player));
    }

    @Test
    public void shopOrderUsesAccountNameWhenMessageIsAbsentOrBlank() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        KoFiOrderProcessor processor = processor(wallet);
        UUID player = UUID.randomUUID();
        JsonObject event = event(null);
        event.addProperty("from_name", " PlayerOne ");
        assertEquals(KoFiOrderProcessor.Result.APPLIED, processor.process(form(event), 1, name -> {
            assertEquals("PlayerOne", name);
            return player;
        }));
        event.addProperty("kofi_transaction_id", "order-2");
        event.addProperty("message", "  ");
        assertEquals(KoFiOrderProcessor.Result.APPLIED, processor.process(form(event), 2, name -> player));
        assertEquals(400, wallet.balance(player));
    }

    @Test
    public void explicitInvalidMessageDoesNotFallBackToAccountName() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        JsonObject event = event("not a username");
        event.addProperty("from_name", "ValidPlayer");
        assertEquals(KoFiOrderProcessor.Result.PENDING, processor(wallet).process(form(event), 1, name -> {
            fail("Invalid explicit recipient must not fall back");
            return null;
        }));
    }

    @Test
    public void invalidAccountNameStaysPending() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        JsonObject event = event(null);
        event.addProperty("from_name", "Not a Minecraft name");
        assertEquals(KoFiOrderProcessor.Result.PENDING, processor(wallet).process(form(event), 1, name -> {
            fail("Invalid account name must not be resolved");
            return null;
        }));
    }

    @Test
    public void invalidOrUnknownUsernameIsPendingAndBadTokenDoesNotResolve() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        KoFiOrderProcessor processor = processor(wallet);
        JsonObject event = event("not a username");
        assertEquals(KoFiOrderProcessor.Result.PENDING, processor.process(form(event), 1, name -> {
            fail("Invalid username was resolved");
            return null;
        }));
        assertEquals(Long.valueOf(200), wallet.pendingPayments().get("order-1"));

        JsonObject unknown = event("UnknownPlayer");
        unknown.addProperty("kofi_transaction_id", "order-2");
        assertEquals(KoFiOrderProcessor.Result.PENDING, processor.process(form(unknown), 2, name -> null));
        unknown.addProperty("verification_token", "wrong");
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(unknown), 3, name -> {
            fail("Unverified order was resolved");
            return null;
        }));
    }

    @Test
    public void draftListingVariationIsPricedAndCredited() throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of("gangsgems-discord.example.json")))
            .getAsJsonObject();
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        KoFiOrderProcessor processor = new KoFiOrderProcessor("secret", WebhookConfiguration.offers(config), wallet);
        UUID player = UUID.randomUUID();
        JsonObject event = event("GiftPlayer");
        JsonObject item = event.getAsJsonArray("shop_items").get(0).getAsJsonObject();
        item.addProperty("direct_link_code", "78370a8b97");
        item.addProperty("variation_name", "Gang Gem x 1500");
        item.addProperty("quantity", 1);
        event.addProperty("amount", "24.99");
        assertThrows(KoFiOrderProcessor.InvalidRequest.class, () -> processor.process(form(event), 1, name -> player));
        event.addProperty("amount", "25.00");
        assertEquals(KoFiOrderProcessor.Result.APPLIED, processor.process(form(event), 1, name -> player));
        assertEquals(1500, wallet.balance(player));
    }

    private static KoFiOrderProcessor processor(WalletStore wallet) {
        return new KoFiOrderProcessor("secret", Map.of(
            new KoFiOrderProcessor.Product("sku", null), new KoFiOrderProcessor.Offer(200, 100)), wallet);
    }

    private static JsonObject event(String message) {
        JsonObject event = new JsonObject();
        event.addProperty("verification_token", "secret");
        event.addProperty("type", "Shop Order");
        event.addProperty("currency", "USD");
        event.addProperty("kofi_transaction_id", "order-1");
        event.addProperty("amount", "4.00");
        event.addProperty("message", message);
        JsonObject item = new JsonObject();
        item.addProperty("direct_link_code", "sku");
        item.addProperty("quantity", 2);
        JsonArray items = new JsonArray();
        items.add(item);
        event.add("shop_items", items);
        return event;
    }

    private static String form(JsonObject event) {
        return "data=" + URLEncoder.encode(event.toString(), StandardCharsets.UTF_8);
    }
}
