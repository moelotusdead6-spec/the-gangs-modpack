package com.gangsboots.mod.client;

import com.gangsboots.mod.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;

public class GangsBootsClient implements ClientModInitializer {
    private static final Identifier BOOTS_TEXTURE = new Identifier("gangsboots", "textures/models/armor/boots_of_swiftness_layer_1_dungeons.png");
    private static final BipedEntityModel<LivingEntity> BOOTS_MODEL = createBootsModel();

    @Override
    public void onInitializeClient() {
        ArmorRenderer.register((matrices, vertexConsumers, stack, entity, slot, light, contextModel) -> {
            if (slot != EquipmentSlot.FEET) {
                return;
            }

            contextModel.copyBipedStateTo(BOOTS_MODEL);
            BOOTS_MODEL.setVisible(false);
            BOOTS_MODEL.leftLeg.visible = true;
            BOOTS_MODEL.rightLeg.visible = true;
            ArmorRenderer.renderPart(matrices, vertexConsumers, light, stack, BOOTS_MODEL, BOOTS_TEXTURE);
        }, ModItems.BOOTS_OF_LIGHTNESS);

        ClientTickEvents.END_CLIENT_TICK.register(GangsBootsClient::spawnFaintFlightParticles);
    }

    private static void spawnFaintFlightParticles(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || !player.getAbilities().flying) {
            return;
        }

        if (!player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.BOOTS_OF_LIGHTNESS)) {
            return;
        }

        // Keep this very subtle: low spawn chance and tiny drift.
        if (player.age % 6 != 0) {
            return;
        }

        double y = player.getY() + 0.08;
        player.getWorld().addParticle(ParticleTypes.CLOUD, player.getX() + 0.18, y, player.getZ() + 0.07, 0.0, 0.002, 0.0);
        player.getWorld().addParticle(ParticleTypes.CLOUD, player.getX() - 0.18, y, player.getZ() - 0.07, 0.0, 0.002, 0.0);
    }

    private static BipedEntityModel<LivingEntity> createBootsModel() {
        ModelData modelData = BipedEntityModel.getModelData(new Dilation(0.45f), 0.0f);
        ModelPartData root = modelData.getRoot();
        TexturedModelData textured = TexturedModelData.of(modelData, 64, 64);
        ModelPart modelRoot = textured.createModel();
        return new BipedEntityModel<>(modelRoot);
    }
}
