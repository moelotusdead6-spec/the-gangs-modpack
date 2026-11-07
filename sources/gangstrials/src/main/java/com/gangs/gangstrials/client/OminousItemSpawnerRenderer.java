package com.gangs.gangstrials.client;

import com.drd.trickytrialsbackport.entity.OminousItemSpawner;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Tricky Trials Backport registers the ominous item spawner entity without a client renderer, crashing clients. */
public final class OminousItemSpawnerRenderer extends EntityRenderer<OminousItemSpawner> {
    private final ItemRenderer itemRenderer;

    public OminousItemSpawnerRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(OminousItemSpawner entity, float yaw, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {
        ItemStack stack = entity.getItem();
        if (!stack.isEmpty()) {
            float age = entity.age + tickDelta;
            float scale = Math.min(age / 20.0F, 1.0F);
            matrices.push();
            matrices.translate(0.0F, 0.25F, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(age / 20.0F));
            matrices.scale(scale, scale, scale);
            this.itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), entity.getId());
            matrices.pop();
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(OminousItemSpawner entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
