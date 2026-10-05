package com.thegangs.gangshats.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;

public class GangsHatFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final ItemRenderer itemRenderer;

    public GangsHatFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
            ItemRenderer itemRenderer) {
        super(context);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch) {
        renderSelected(matrices, vertexConsumers, light, player, "halo");
    }

    private void renderSelected(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, String slot) {
        String rewardId = ClientCosmeticState.selected(player.getUuid(), slot);
        if (rewardId == null) {
            return;
        }
        Item item = Registries.ITEM.get(new Identifier(rewardId));
        if (item == net.minecraft.item.Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        matrices.push();
        getContextModel().head.rotate(matrices);
        matrices.translate(0.0D, -0.85D, 0.0D);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.scale(0.65F, 0.65F, 0.65F);
        itemRenderer.renderItem(player, stack, ModelTransformationMode.HEAD, false, matrices, vertexConsumers,
                player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
        matrices.pop();
    }

}