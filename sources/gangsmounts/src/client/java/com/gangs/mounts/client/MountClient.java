package com.gangs.mounts.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cosmeticsmod.morecosmetics.gui.WardrobeGui;
import com.gangs.mounts.MountService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class MountClient implements ClientModInitializer {
	private static final Map<String, NbtCompound> PREVIEWS = new LinkedHashMap<>();
	private static KeyBinding summon;
	private static KeyBinding recall;

	@Override
	public void onInitializeClient() {
		summon = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gangsmounts.summon",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.gangsmounts"));
		recall = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gangsmounts.recall",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.gangsmounts"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (summon.wasPressed()) sendQuickAction(client, true);
			while (recall.wasPressed()) sendQuickAction(client, false);
		});
		ClientPlayNetworking.registerGlobalReceiver(MountService.CATALOG, (client, handler, buffer, sender) -> {
			int count = buffer.readVarInt();
			if (count < 1 || count > 128) throw new IllegalArgumentException("Invalid mount catalog size");
			Map<String, NbtCompound> entries = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				String id = buffer.readString(256);
				NbtCompound nbt = buffer.readNbt();
				if (nbt == null) throw new IllegalArgumentException("Missing mount preview NBT: " + id);
				entries.put(id, nbt);
			}
			client.execute(() -> {
				for (var entry : entries.entrySet()) {
					var entity = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.tryParse(entry.getKey())).create(client.world);
					if (!(entity instanceof net.minecraft.entity.LivingEntity living)) {
						throw new IllegalStateException("Mount preview type is not living: " + entry.getKey());
					}
					living.readNbt(entry.getValue().copy());
					var texture = client.getEntityRenderDispatcher().getRenderer(living).getTexture(living);
					if (client.getResourceManager().getResource(texture).isEmpty()) {
						throw new IllegalStateException("Missing mount texture: " + entry.getKey() + " -> " + texture);
					}
				}
				PREVIEWS.clear();
				PREVIEWS.putAll(entries);
				com.thegangs.gangshats.GangsHats.LOGGER.info("Verified {} mount preview texture resources against the unchanged live wardrobe.", entries.size());
				WardrobeGui.refreshGui();
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PREVIEWS.clear());
	}
	private static void sendQuickAction(MinecraftClient client, boolean summon) {
		if (client.player == null || client.currentScreen != null) return;
		if (!ClientPlayNetworking.canSend(MountService.QUICK_ACTION)) {
			client.player.sendMessage(Text.literal("Mount shortcuts are unavailable on this server."), true);
			return;
		}
		var buffer = PacketByteBufs.create();
		buffer.writeBoolean(summon);
		ClientPlayNetworking.send(MountService.QUICK_ACTION, buffer);
	}
	public static List<String> ids() { return List.copyOf(PREVIEWS.keySet()); }
	public static NbtCompound preview(String id) { return PREVIEWS.get(id); }
	public static KeyBinding summonKey() { return summon; }
	public static KeyBinding recallKey() { return recall; }
}
