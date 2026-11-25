package com.gangs.mounts.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.cosmeticsmod.morecosmetics.gui.WardrobeGui;
import com.cosmeticsmod.morecosmetics.gui.core.box.utils.BoxCategory;
import com.gangs.mounts.MountService;
import com.gangs.mounts.mixin.BoxManagerAccess;
import com.gangs.mounts.mixin.WardrobeManagerAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
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
	private static Map<String, NbtCompound> pendingCatalog = Map.of();
	private static KeyBinding summon;
	private static KeyBinding recall;
	private static String variantType;
	private static boolean openMounts;
	private static boolean selectMounts;

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("mounts").executes(context -> {
					openMounts = true;
					return 1;
				})));
		summon = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gangsmounts.summon",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.gangsmounts"));
		recall = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gangsmounts.recall",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.gangsmounts"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			applyPendingCatalog(client);
			if (openMounts) {
				openMounts = false;
				openMounts(client);
			}
			while (summon.wasPressed()) sendQuickAction(client, true);
			while (recall.wasPressed()) sendQuickAction(client, false);
		});
		ClientPlayNetworking.registerGlobalReceiver(MountService.CATALOG, (client, handler, buffer, sender) -> {
			int count = buffer.readVarInt();
			if (count < 1 || count > 512) throw new IllegalArgumentException("Invalid mount catalog size");
			Map<String, NbtCompound> entries = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				String id = buffer.readString(256);
				NbtCompound nbt = buffer.readNbt();
				if (nbt == null) throw new IllegalArgumentException("Missing mount preview NBT: " + id);
				entries.put(id, nbt);
			}
			client.execute(() -> pendingCatalog = entries);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PREVIEWS.clear();
			pendingCatalog = Map.of();
			variantType = null;
			openMounts = false;
			selectMounts = false;
		});
	}
	private static void openMounts(MinecraftClient client) {
		if (client.player == null) return;
		if (PREVIEWS.isEmpty()) {
			client.player.sendMessage(Text.literal("Mounts are unavailable on this server or still loading."), false);
			return;
		}
		showVariants(null);
		selectMounts = true;
		v1_20.morecosmetics.gui.screen.WardrobeUI.displayUI();
	}
	public static void selectRequestedMounts(List<BoxCategory> categories) {
		if (!selectMounts) return;
		selectMounts = false;
		var manager = ((WardrobeManagerAccess) v1_20.morecosmetics.gui.screen.WardrobeUI.getScreen().getUI())
				.gangsmounts$boxManager();
		for (int i = 0; i < categories.size(); i++) {
			if (!categories.get(i).getName().equals("Mounts")) continue;
			var access = (BoxManagerAccess) manager;
			access.gangsmounts$selectedCategory(i);
			access.gangsmounts$selectedEntry(null);
			manager.setExtensionVisible(false);
			manager.fetchCategories();
			return;
		}
		throw new IllegalStateException("The mounts category is unavailable in the cosmetics menu.");
	}
	private static void applyPendingCatalog(MinecraftClient client) {
		if (pendingCatalog.isEmpty() || client.world == null || client.getOverlay() != null) return;
		for (var entry : pendingCatalog.entrySet()) {
			var entity = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.tryParse(MountService.entityId(entry.getKey()))).create(client.world);
			if (!(entity instanceof net.minecraft.entity.LivingEntity living)) {
				throw new IllegalStateException("Mount preview type is not living: " + entry.getKey());
			}
			living.readNbt(entry.getValue().copy());
			var renderer = client.getEntityRenderDispatcher().getRenderer(living);
			if (renderer == null) throw new IllegalStateException("Missing mount renderer: " + entry.getKey());
			var texture = renderer.getTexture(living);
			if (client.getResourceManager().getResource(texture).isEmpty()) {
				throw new IllegalStateException("Missing mount texture: " + entry.getKey() + " -> " + texture);
			}
		}
		PREVIEWS.clear();
		PREVIEWS.putAll(pendingCatalog);
		pendingCatalog = Map.of();
		com.thegangs.gangshats.GangsHats.LOGGER.info("Verified {} mount preview texture resources against the unchanged live wardrobe.", PREVIEWS.size());
		WardrobeGui.refreshGui();
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
	public static List<String> types() { return ids().stream().map(MountService::entityId).distinct().toList(); }
	public static List<String> variants(String type) { return ids().stream().filter(id -> MountService.entityId(id).equals(type)).toList(); }
	public static String variantType() { return variantType; }
	public static void showVariants(String type) {
		variantType = type;
		WardrobeGui.refreshGui();
	}
	public static KeyBinding summonKey() { return summon; }
	public static KeyBinding recallKey() { return recall; }
}
