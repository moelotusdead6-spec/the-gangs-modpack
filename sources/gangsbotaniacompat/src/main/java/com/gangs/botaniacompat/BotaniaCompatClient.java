package com.gangs.botaniacompat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class BotaniaCompatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (Registries.ITEM.getId(stack.getItem()).toString().equals(BotaniaCompat.TIARA)) {
                lines.add(Text.literal(BotaniaCompat.NOTICE).formatted(Formatting.RED));
            }
        });
    }
}
