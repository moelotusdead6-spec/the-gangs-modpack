package com.elysium.goldclaim;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.util.Identifier;

/** Registers {@code %goldclaim:kit_ready <w1|w2|w3|m>%} for the SkiesKits claim menu. Load only when placeholder-api is present. */
final class KitPlaceholders {
    private KitPlaceholders() {}

    static void register(KitService kits) {
        Placeholders.register(new Identifier("goldclaim", "kit_ready"), (context, argument) -> {
            int kit = KitService.kitIndex(argument == null ? "" : argument.trim());
            if (kit < 0) {
                return PlaceholderResult.invalid("Unknown kit; use w1, w2, w3 or m");
            }
            if (!context.hasPlayer()) {
                return PlaceholderResult.invalid("No player");
            }
            return PlaceholderResult.value(kits.readyStatus(context.server(), context.player().getGameProfile().getName(), kit));
        });
    }
}
