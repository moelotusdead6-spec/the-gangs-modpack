package com.thegangs.gangshats.client;

import com.thegangs.gangshats.HatRenderClassifier;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SkullItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GangsHatFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GangsHatFeatureRenderer.class);
    private static final float BLOCK_HAT_SCALE = 0.72F;
    private static final float CROWN_HAT_SCALE = 0.55F;

    // Avoids re-logging every frame when the same broken hat is worn.
    private Item lastFailedHatItem;

    private final ItemRenderer itemRenderer;
    private final BlockRenderManager blockRenderManager;

    public GangsHatFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
            ItemRenderer itemRenderer, BlockRenderManager blockRenderManager) {
        super(context);
        this.itemRenderer = itemRenderer;
        this.blockRenderManager = blockRenderManager;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch) {
        ItemStack hatStack = player.getEquippedStack(EquipmentSlot.HEAD);
        if (shouldUseVanillaRenderer(hatStack)) {
            return;
        }

        matrices.push();
        getContextModel().head.rotate(matrices);

        try {
            if (HatRenderClassifier.shouldRenderAsPlacedBlock(hatStack)) {
                renderBlockHat(matrices, vertexConsumers, light, hatStack);
            } else if (HatRenderClassifier.shouldRenderAsCrown(hatStack)) {
                renderCrownHat(matrices, vertexConsumers, light, player, hatStack);
            }
        } catch (Exception e) {
            // Some modded block/item models (e.g. custom BuiltinModelItemRenderer furniture) throw
            // when rendered outside their normal block-entity context; never let that crash the client.
            if (hatStack.getItem() != lastFailedHatItem) {
                lastFailedHatItem = hatStack.getItem();
                LOGGER.error("Failed to render hat item {}", Registries.ITEM.getId(hatStack.getItem()), e);
            }
        }

        matrices.pop();
    }

    private static boolean shouldUseVanillaRenderer(ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof ArmorItem || stack.getItem() instanceof SkullItem;
    }

    private void renderBlockHat(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            ItemStack stack) {
        BlockItem blockItem = (BlockItem) stack.getItem();

        matrices.translate(0.0D, -0.28D, 0.0D);
        matrices.scale(BLOCK_HAT_SCALE, -BLOCK_HAT_SCALE, -BLOCK_HAT_SCALE);
        matrices.translate(-0.5D, -0.5D, -0.5D);
        blockRenderManager.renderBlockAsEntity(blockItem.getBlock().getDefaultState(), matrices, vertexConsumers, light,
                OverlayTexture.DEFAULT_UV);
    }

    private void renderCrownHat(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            AbstractClientPlayerEntity player, ItemStack stack) {
        matrices.translate(0.0D, -0.72D, 0.0D);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.scale(CROWN_HAT_SCALE, CROWN_HAT_SCALE, CROWN_HAT_SCALE);
        itemRenderer.renderItem(player, stack, ModelTransformationMode.HEAD, false, matrices, vertexConsumers,
                player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
    }
}