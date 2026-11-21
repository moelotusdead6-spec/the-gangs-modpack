package com.gangs.gangshop.shop;

import com.gangs.gangshop.economy.WalletStore;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WalletOverflowTest {
    @Test
    public void overflowingCreditDoesNotChangeBalance() {
        WalletStore wallets = new WalletStore();
        UUID player = UUID.randomUUID();
        wallets.setBalance(player, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> wallets.addBalance(player, 1));
        assertEquals(Long.MAX_VALUE, wallets.getBalance(player));
    }
}
