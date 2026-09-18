package com.thegangs.gangshats.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

import com.thegangs.gangshats.GangsHats;
import com.thegangs.gangshats.PetEntities;

public class GangsHatsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(PetEntities.GANG_PET,
            GangPetRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(GangsHats.COSMETIC_SELECTION_PACKET,
                (client, handler, buffer, responseSender) -> {
                    String slot = buffer.readString();
                    String rewardId = buffer.readString();
                    java.util.UUID playerId = buffer.readUuid();
                    client.execute(() -> ClientCosmeticState.select(playerId, slot, rewardId));
                });
        LivingEntityFeatureRendererRegistrationCallback.EVENT
                .register((entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                        registrationHelper.register(new GangsHatFeatureRenderer(playerRenderer,
                                context.getItemRenderer(), context.getBlockRenderManager()));
                    }
                });
    }
}