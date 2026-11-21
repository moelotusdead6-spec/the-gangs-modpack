package com.gangs.gangshop.shop;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TradeMathTest {
    @Test
    public void computesExactTotalsAndSaleBalances() {
        assertEquals(640000L, TradeMath.total(10000L, 64));
        assertEquals(Long.MAX_VALUE, TradeMath.balanceAfterSale(Long.MAX_VALUE - 3, 3));
    }

    @Test
    public void rejectsOverflowBeforeAnInventoryOrWalletMutation() {
        assertThrows(ArithmeticException.class, () -> TradeMath.total(Long.MAX_VALUE, 2));
        assertThrows(ArithmeticException.class, () -> TradeMath.balanceAfterSale(Long.MAX_VALUE, 1));
    }

    @Test
    public void rejectsNegativeAmountsAndInvalidQuantities() {
        assertThrows(IllegalArgumentException.class, () -> TradeMath.total(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> TradeMath.total(1, 0));
        assertThrows(IllegalArgumentException.class, () -> TradeMath.balanceAfterSale(-1, 1));
    }
}
