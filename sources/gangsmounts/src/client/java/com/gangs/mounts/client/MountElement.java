package com.gangs.mounts.client;

import com.cosmeticsmod.morecosmetics.gui.core.UIConstants;
import com.cosmeticsmod.morecosmetics.gui.core.box.utils.BoxComponent;
import com.cosmeticsmod.morecosmetics.utils.ModConfig;
import com.gangs.mounts.MountService;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thegangs.gangshats.client.ClientWardrobeState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import v1_20.morecosmetics.DrawUtils;
import v1_20.morecosmetics.models.renderer.StackHolder;

public final class MountElement extends BoxComponent {
	private final String id;
	private final LivingEntity preview;
	private final boolean species;
	private double lastRenderTick = Double.NaN;
	private float rotation = 190;

	public MountElement(String id) {
		this(id, false);
	}

	public MountElement(String id, boolean species) {
		super(title(id, species), true);
		this.id = id;
		this.species = species;
		var entity = Registries.ENTITY_TYPE.get(Identifier.tryParse(MountService.entityId(id))).create(MinecraftClient.getInstance().world);
		if (!(entity instanceof LivingEntity living)) throw new IllegalStateException("Cannot preview mount: " + id);
		preview = living;
		preview.readNbt(MountClient.preview(id).copy());
		preview.refreshPositionAndAngles(0, 0, 0, 0, 0);
		preview.prevYaw = preview.bodyYaw = preview.prevBodyYaw = preview.headYaw = preview.prevHeadYaw = 0;
		preview.prevPitch = 0;
		preview.setVelocity(0, 0, 0);
	}

	private static String title(String id, boolean species) {
		String type = MountService.entityId(id);
		String name = Registries.ENTITY_TYPE.get(Identifier.tryParse(type)).getName().getString();
		return species ? name : name + " - Variant " + (MountClient.variants(type).indexOf(id) + 1);
	}

	@Override
	public void drawComponent(Object stack, int x, int y, int width, int height, int mouseX, int mouseY) {
		super.drawComponent(stack, x, y, width, height, mouseX, mouseY);
		boolean owned = ClientWardrobeState.isUnlocked(id);
		boolean active = id.equals(ClientWardrobeState.selected(MountService.SLOT));
		DrawUtils.drawRoundedRect(x, y, x + width, y + height,
				active ? UIConstants.UI_ACCENT_COLOR : UIConstants.UI_SEPARATION_COLOR, 4);
		DrawUtils.drawRoundedRect(x + 1, y + 1, x + width - 1, y + height - 1,
				mouseOver ? UIConstants.UI_COMPONENT_HOVER : UIConstants.UI_COMPONENT_COLOR, 3);
		var client = MinecraftClient.getInstance();
		float tickDelta = client.getTickDelta();
		// GeckoLib adds client tick delta to age; an unticked preview otherwise jumps backward every tick.
		preview.age = client.player.age;
		double renderTick = preview.age + (double) tickDelta;
		if (mouseOver && ModConfig.getConfig().animatedPreview && !Double.isNaN(lastRenderTick)) {
			rotation = (rotation + (float) Math.max(0, Math.min(2, renderTick - lastRenderTick))) % 360;
		}
		lastRenderTick = renderTick;
		var dispatcher = client.getEntityRenderDispatcher();
		var matrices = StackHolder.STACK;
		var buffers = client.getBufferBuilders().getEntityVertexConsumers();
		float scale = (float) Math.min(22, (height - 16) / Math.max(preview.getHeight(), preview.getWidth()));
		matrices.push();
		matrices.translate(x + width / 2.0, y + height - 14, 100);
		matrices.scale(scale, -scale, scale);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
		DiffuseLighting.disableGuiDepthLighting();
		dispatcher.setRenderShadows(false);
		try {
			RenderSystem.runAsFancy(() -> dispatcher.render(preview, 0, 0, 0, 0, tickDelta, matrices, buffers, 15728880));
			buffers.draw();
		} finally {
			dispatcher.setRenderShadows(true);
			matrices.pop();
			DiffuseLighting.enableGuiDepthLighting();
		}
		DrawUtils.drawCenteredString(title, x + width / 2.0f, y + height - 8, 0.7f);
		if (mouseOver) DrawUtils.drawToolTip(species ? "Click to choose a variant" : !owned ? "Preview only - locked" : active ? "Click to recall and store" : "Click to summon", mouseX, mouseY);
	}

	@Override
	public void mouseClicked(int x, int y, int button) {
		if (!mouseOver || !enabled || button != UIConstants.MOUSE_LEFT) return;
		if (species) {
			MountClient.showVariants(MountService.entityId(id));
			return;
		}
		if (!ClientWardrobeState.isUnlocked(id)) return;
		if (id.equals(ClientWardrobeState.selected(MountService.SLOT))) ClientWardrobeState.requestClear(MountService.SLOT);
		else ClientWardrobeState.requestSelect(MountService.SLOT, id);
	}
}
