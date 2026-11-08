package com.gangs.chefcompat;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TradeSmokeTest implements DedicatedServerModInitializer {
    private record Trade(int method, String item, int amount, int emeralds, int experience) {}

    @Override
    public void onInitializeServer() {
        List<Trade> trades = List.of(
                new Trade(57, "pie_crust", 4, 1, 10),
                new Trade(42, "pumpkin_slice", 12, 1, 10),
                new Trade(27, "bacon", 4, 1, 3),
                new Trade(23, "salmon_roll", 4, 2, 10),
                new Trade(22, "kelp_roll", 3, 2, 10),
                new Trade(17, "raw_pasta", 9, 1, 10),
                new Trade(16, "wheat_dough", 9, 1, 10),
                new Trade(11, "pie_crust", 3, 2, 12),
                new Trade(10, "rice", 9, 2, 12));
        try {
            Class<?> villager = Class.forName("net.redstonegames.chefsdelight.villager.ModVillagers");
            for (Trade trade : trades) {
                TradeOffer offer = invoke(villager, trade.method());
                ItemStack buy = offer.getOriginalFirstBuyItem();
                require(Registries.ITEM.getId(buy.getItem()).equals(new Identifier("farmersdelight", trade.item())),
                        "Wrong item in trade " + trade.method());
                require(buy.getCount() == trade.amount(), "Wrong item count in trade " + trade.method());
                require(offer.getSellItem().isOf(Items.EMERALD)
                                && offer.getSellItem().getCount() == trade.emeralds(),
                        "Wrong emerald payment in trade " + trade.method());
                require(offer.getMaxUses() == 12 && offer.getMerchantExperience() == trade.experience()
                                && offer.getPriceMultiplier() == 0.05F,
                        "Wrong offer settings in trade " + trade.method());
            }

            TradeOffer unaffected = invoke(villager, 38);
            require(unaffected.getOriginalFirstBuyItem().isOf(Items.EMERALD)
                            && unaffected.getOriginalFirstBuyItem().getCount() == 2,
                    "Unrelated trade cost changed");
            require(Registries.ITEM.getId(unaffected.getSellItem().getItem())
                            .equals(new Identifier("farmersdelight", "bacon_sandwich"))
                            && unaffected.getSellItem().getCount() == 1,
                    "Unrelated trade result changed");
            require(unaffected.getMaxUses() == 12 && unaffected.getMerchantExperience() == 10
                            && unaffected.getPriceMultiplier() == 0.05F,
                    "Unrelated trade settings changed");

            Files.writeString(Path.of("trade-smoke-passed.txt"),
                    "PASS: nine repaired trade factories and one unaffected trade\n");
            LoggerFactory.getLogger("gangschefcompat_smoke").info(
                    "PASS: nine repaired trade factories and one unaffected trade");
        } catch (ReflectiveOperationException | IOException failure) {
            throw new IllegalStateException("Chef trade compatibility smoke test failed", failure);
        }
        System.exit(0);
    }

    private static TradeOffer invoke(Class<?> villager, int number) throws ReflectiveOperationException {
        Method method = villager.getDeclaredMethod("lambda$registerTrades$" + number, Entity.class, Random.class);
        method.setAccessible(true);
        return (TradeOffer) method.invoke(null, null, null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
