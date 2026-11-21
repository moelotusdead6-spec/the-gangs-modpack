package com.gangs.gangshop.shop;

public final class TradeMath {
    private TradeMath() {
    }

    public static long total(long unitPrice, int quantity) {
        if (unitPrice < 0 || quantity <= 0) {
            throw new IllegalArgumentException("Invalid price or quantity");
        }
        return Math.multiplyExact(unitPrice, (long) quantity);
    }

    public static long balanceAfterSale(long balance, long total) {
        if (balance < 0 || total < 0) {
            throw new IllegalArgumentException("Invalid balance or payout");
        }
        return Math.addExact(balance, total);
    }
}
