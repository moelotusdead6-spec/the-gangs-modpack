package com.gangs.mounts.client;

import com.cosmeticsmod.morecosmetics.gui.core.UIConstants;
import com.cosmeticsmod.morecosmetics.gui.core.box.utils.BoxComponent;
import v1_20.morecosmetics.DrawUtils;

public final class MountBackElement extends BoxComponent {
	public MountBackElement() { super("Back to all mounts", true); }

	@Override
	public void drawComponent(Object stack, int x, int y, int width, int height, int mouseX, int mouseY) {
		super.drawComponent(stack, x, y, width, height, mouseX, mouseY);
		DrawUtils.drawRoundedRect(x, y, x + width, y + height,
				mouseOver ? UIConstants.UI_COMPONENT_HOVER : UIConstants.UI_COMPONENT_COLOR, 4);
		DrawUtils.drawCenteredString(title, x + width / 2.0f, y + height / 2.0f, 0.7f);
	}

	@Override
	public void mouseClicked(int x, int y, int button) {
		if (mouseOver && enabled && button == UIConstants.MOUSE_LEFT) MountClient.showVariants(null);
	}
}
