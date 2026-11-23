package com.gangs.gangsgems;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WalletStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void persistsCreditsSpendingAndNotifications() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        assertEquals(WalletStore.CreditResult.APPLIED, wallet.creditPayment("order-1", player, 100));
        assertFalse(wallet.spend(player, 101));
        assertTrue(wallet.spend(player, 30));

        WalletStore reloaded = new WalletStore(file);
        assertEquals(70, reloaded.balance(player));
        assertEquals(100, reloaded.unannounced(player));
        assertEquals(WalletStore.CreditResult.DUPLICATE, reloaded.creditPayment("order-1", player, 100));
        assertEquals(70, reloaded.balance(player));
        reloaded.acknowledgeNotification(player, 100);
        assertEquals(0, new WalletStore(file).unannounced(player));
    }

    @Test
    public void rejectsConflictingReplayAndCorruptStorage() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        wallet.creditPayment("order-1", player, 100);
        assertThrows(IllegalStateException.class, () -> wallet.creditPayment("order-1", player, 200));
        assertThrows(IllegalStateException.class, () -> wallet.creditPayment("order-1", UUID.randomUUID(), 100));
        assertEquals(100, new WalletStore(file).balance(player));
        Files.writeString(file, "{\"version\":1,\"accounts\":{},\"payments\":{\"order\":{\"player\":\""
            + player + "\",\"gems\":100}}}");
        assertThrows(java.io.IOException.class, () -> new WalletStore(file));
    }

    @Test
    public void failedPersistenceDoesNotChangeMemory() throws Exception {
        Path parent = folder.newFolder().toPath();
        Path file = parent.resolve("wallet.json");
        WalletStore wallet = new WalletStore(file);
        Files.delete(parent);
        Files.writeString(parent, "not a directory");
        UUID player = UUID.randomUUID();
        assertThrows(java.io.IOException.class, () -> wallet.creditPayment("order", player, 100));
        assertEquals(0, wallet.balance(player));
    }

    @Test
    public void linksPaymentsOnceAndKeepsUnmatchedPaymentsPending() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        String code = wallet.createLink(player, 1000);
        assertFalse(Files.readString(file).contains(code));
        wallet = new WalletStore(file);
        assertEquals(WalletStore.CreditResult.APPLIED, wallet.recordPayment("one", code, 100, 1001));
        assertEquals(WalletStore.CreditResult.DUPLICATE, wallet.recordPayment("one", code, 100, 1002));
        assertEquals(WalletStore.CreditResult.PENDING, wallet.recordPayment("two", code, 200, 1003));
        assertEquals(100, wallet.balance(player));
        wallet.assignPending("two", player);
        assertEquals(300, new WalletStore(file).balance(player));
    }

    @Test
    public void expiredLinkDoesNotCredit() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        UUID player = UUID.randomUUID();
        String code = wallet.createLink(player, 1000);
        assertEquals(WalletStore.CreditResult.PENDING, wallet.recordPayment("order", code, 100, 1000 + 24 * 60 * 60 * 1000L));
        assertEquals(0, wallet.balance(player));
    }

    @Test
    public void notificationReceiptDoesNotClearLaterPurchases() throws Exception {
        WalletStore wallet = new WalletStore(folder.getRoot().toPath().resolve("wallet.json"));
        UUID player = UUID.randomUUID();
        wallet.creditPayment("first", player, 100);
        long observed = wallet.unannounced(player);
        wallet.creditPayment("second", player, 200);
        wallet.acknowledgeNotification(player, observed);
        assertEquals(200, wallet.unannounced(player));
    }

    @Test
    public void craftingReservationAndDebitArePersistedTogether() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        wallet.creditPayment("order", player, 100);
        WalletStore.Craft craft = wallet.reserveCraft(player, 30, "{id:\"example\"}");
        wallet = new WalletStore(file);
        assertEquals(70, wallet.balance(player));
        assertEquals(java.util.List.of(craft), wallet.pendingCrafts(player));
        wallet.completeCraft(craft.id(), player);
        assertTrue(new WalletStore(file).pendingCrafts(player).isEmpty());
    }

    @Test
    public void staffAdjustmentsAndAuditPersistAtomically() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        wallet.adjust(player, 100, false, "staff-uuid", "Event prize");
        wallet.adjust(player, 30, true, "admin-uuid", "Correction");
        assertThrows(IllegalStateException.class, () -> wallet.adjust(player, 71, true, "admin-uuid", "Too much"));
        assertThrows(IllegalArgumentException.class, () -> wallet.adjust(player, 1, false, "staff-uuid", " "));
        WalletStore reloaded = new WalletStore(file);
        assertEquals(70, reloaded.balance(player));
        assertEquals(2, reloaded.audit(player).size());
        assertTrue(reloaded.audit(player).stream().anyMatch(a -> a.action().equals("remove")
            && a.actor().equals("admin-uuid") && a.reason().equals("Correction")));
        assertNull(reloaded.payment("Event prize"));
    }

    @Test
    public void staffPurchaseRecoveryRecordsOneAuditPerChange() throws Exception {
        Path file = folder.getRoot().toPath().resolve("wallet.json");
        UUID player = UUID.randomUUID();
        WalletStore wallet = new WalletStore(file);
        assertEquals(WalletStore.CreditResult.APPLIED, wallet.staffCreditPayment("order", player, 100, "console"));
        assertEquals(WalletStore.CreditResult.DUPLICATE, wallet.staffCreditPayment("order", player, 100, "console"));
        wallet.recordPaymentFor("pending", null, 200);
        wallet.staffAssignPending("pending", player, "console");
        WalletStore reloaded = new WalletStore(file);
        assertEquals(300, reloaded.balance(player));
        assertEquals(new WalletStore.PaymentView(player, 200), reloaded.payment("pending"));
        assertEquals(java.util.List.of(new WalletStore.RecordedPayment("order", 100),
            new WalletStore.RecordedPayment("pending", 200)), reloaded.paymentsFor(player));
        assertEquals(2, reloaded.audit(player).size());
        assertTrue(reloaded.audit(player).stream().anyMatch(a -> a.action().equals("assign")
            && a.reference().equals("pending")));
    }

    @Test
    public void failedStaffAdjustmentDoesNotChangeBalanceOrAudit() throws Exception {
        Path parent = folder.newFolder().toPath();
        WalletStore wallet = new WalletStore(parent.resolve("wallet.json"));
        Files.delete(parent);
        Files.writeString(parent, "not a directory");
        UUID player = UUID.randomUUID();
        assertThrows(java.io.IOException.class, () -> wallet.adjust(player, 100, false, "console", "Test"));
        assertEquals(0, wallet.balance(player));
        assertTrue(wallet.audit(player).isEmpty());
    }
}
