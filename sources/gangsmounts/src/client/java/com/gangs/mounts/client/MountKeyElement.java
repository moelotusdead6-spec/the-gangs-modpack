package com.gangs.mounts.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import v1_20.morecosmetics.gui.components.CustomTextBox;
import v1_20.morecosmetics.gui.elements.list.BaseElement;

public final class MountKeyElement extends BaseElement {
	private static MountKeyElement waiting;
	private final KeyBinding binding;
	private final CustomTextBox box = new CustomTextBox(0, 0, 86, 16);

	public MountKeyElement(String title, KeyBinding binding) {
		super(title);
		this.binding = binding;
		box.setMaxLength(80);
		box.setCentered(true);
		box.setCursorDisplayEnabled(false);
	}

	@Override
	public void drawComponent(Object stack, int x, int y, int width, int height, int mouseX, int mouseY) {
		super.drawComponent(stack, x, y, width, height, mouseX, mouseY);
		box.xPosition = x + width - 87;
		box.yPosition = y + 2;
		box.setFocused(waiting == this);
		box.setText(waiting == this ? "Press a key..." : binding.getBoundKeyLocalizedText().getString());
		box.drawTextBox(mouseX, mouseY);
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) {
		super.mouseClicked(mouseX, mouseY, button);
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseOver
				&& mouseX >= box.xPosition && mouseX <= box.xPosition + 86
				&& mouseY >= box.yPosition && mouseY <= box.yPosition + 16) waiting = this;
	}

	public static void cancelCapture() { waiting = null; }

	public static boolean captureKey(int keyCode, int scanCode) {
		if (waiting == null) return false;
		if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
			InputUtil.Key key = keyCode == GLFW.GLFW_KEY_BACKSPACE
					? InputUtil.UNKNOWN_KEY : InputUtil.fromKeyCode(keyCode, scanCode);
			MinecraftClient.getInstance().options.setKeyCode(waiting.binding, key);
			KeyBinding.updateKeysByCode();
			KeyBinding.unpressAll();
			MinecraftClient.getInstance().options.write();
		}
		waiting = null;
		return true;
	}
}
