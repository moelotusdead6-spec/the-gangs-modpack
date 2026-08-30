package com.thegangs.gangshats.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

public class GangsHatsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT
                .register((entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                        registrationHelper.register(new GangsHatFeatureRenderer(playerRenderer,
                                context.getItemRenderer(), context.getBlockRenderManager()));
                    }
                });
    }
}