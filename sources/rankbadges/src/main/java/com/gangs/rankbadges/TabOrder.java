package com.gangs.rankbadges;

public final class TabOrder {
    private TabOrder() {}

    public static int priority(String team) {
        return switch (team) {
            case "00_owner" -> 0;
            case "01_mod" -> 1;
            case "02_gang" -> 2;
            case "03_drifters" -> 3;
            default -> 4;
        };
    }
}
