package com.elysium.goldclaim;

public final class MobCapPolicy {
    // Vanilla caps 70/10/15/5/5/5/20 become 61/9/13/4/4/4/17: 130 -> 112 total (-13.8%).
    static final double CUT = 0.125;

    private MobCapPolicy() {}

    public static int capacity(int vanilla) {
        return vanilla <= 0 ? vanilla : Math.max(1, vanilla - (int)Math.round(vanilla * CUT));
    }
}
