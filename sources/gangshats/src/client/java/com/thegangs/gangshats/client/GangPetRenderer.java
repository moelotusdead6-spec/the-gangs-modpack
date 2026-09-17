package com.thegangs.gangshats.client;

import com.thegangs.gangshats.GangPetEntity;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class GangPetRenderer extends EntityRenderer<GangPetEntity> {
	private final ItemRenderer itemRenderer;

	public GangPetRenderer(EntityRendererFactory.Context context) {
		super(context);
		itemRenderer = context.getItemRenderer();
		shadowRadius = 0.2F;
	}

	@Override
	public void render(GangPetEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.translate(0.0D, 0.35D, 0.0D);
		matrices.scale(0.5F, 0.5F, 0.5F);
		ItemStack stack = new ItemStack(Registries.ITEM.get(new Identifier(entity.getRewardId())));
		itemRenderer.renderItem(stack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, entity.getWorld(), entity.getId());
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(GangPetEntity entity) {
		return new Identifier("minecraft", "textures/misc/white.png");
	}
}
