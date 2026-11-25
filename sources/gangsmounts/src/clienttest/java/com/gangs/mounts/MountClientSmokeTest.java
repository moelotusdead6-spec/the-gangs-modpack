package com.gangs.mounts;

import java.nio.file.Files;
import java.nio.file.Path;
import com.cosmeticsmod.morecosmetics.gui.core.box.BoxManager;
import com.cosmeticsmod.morecosmetics.gui.core.box.utils.BoxCategory;
import com.gangs.mounts.client.MountClient;
import com.gangs.mounts.client.MountElement;
import com.gangs.mounts.client.MountBackElement;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;

public final class MountClientSmokeTest implements ClientModInitializer {
	private boolean started;
	private boolean verified;
	private boolean reloadStarted;
	private boolean wardrobeOpened;
	private boolean shortcutRequested;
	private BoxCategory previousPicker;
	private int pickerIndex;
	private int pickerTicks;
	private int ticks;
	private int stopAtTick;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (++ticks > 6000) throw new IllegalStateException("Isolated client smoke timed out");
			if (verified) {
				if (ticks >= stopAtTick) client.scheduleStop();
				return;
			}
			if (!started && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
				started = true;
				client.createIntegratedServerLoader().start(client.currentScreen, "mount-client-world-1.0.2");
			}
			if (client.world == null || client.player == null || MountClient.ids().isEmpty()) return;
			if (!reloadStarted) {
				if (client.getOverlay() != null) return;
				reloadStarted = true;
				var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
				buffer.writeVarInt(MountClient.ids().size());
				for (String id : MountClient.ids()) {
					buffer.writeString(id);
					var preview = MountClient.preview(id).copy();
					preview.putBoolean("gangsmounts_reload_probe", true);
					buffer.writeNbt(preview);
				}
				var server = client.getServer();
				require(server != null, "Client fixture is not using its isolated integrated server");
				var playerId = client.player.getUuid();
				client.reloadResources();
				require(client.getOverlay() != null, "Resource reload did not start");
				server.execute(() -> {
					var player = server.getPlayerManager().getPlayer(playerId);
					require(player != null, "Reload fixture disconnected");
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, MountService.CATALOG, buffer);
				});
				return;
			}
			boolean probe = MountClient.preview(MountClient.ids().get(0)).getBoolean("gangsmounts_reload_probe");
			if (client.getOverlay() != null) {
				require(!probe, "Mount catalog applied before resource reload finished");
				return;
			}
			if (!probe) return;
			if (shortcutRequested && !wardrobeOpened) {
				if (client.currentScreen != v1_20.morecosmetics.gui.screen.WardrobeUI.getScreen()) return;
				var manager = nativeManager();
				require(manager.getSelectedCategory().getName().equals("Mounts"),
						"/mounts did not select the Mounts category");
				var mounts = manager.getSelectedCategory();
				require(mounts.getTitle().equals("Mounts"), "/mounts did not open the species view");
				var wings = manager.getCategories().stream().filter(c -> c.getName().equals("Wings")).findFirst().orElseThrow();
				require(!mounts.getIconPath().equals(wings.getIconPath()), "Mounts duplicates the Wings icon");
				var icon = new Identifier("minecraft", "morecosmetics/gui/icons/" + mounts.getIconPath() + ".png");
				require(client.getResourceManager().getResource(icon).isPresent(), "Mounts category icon is missing");
				require(mounts.getEntries().size() == MountClient.types().size(), "Species view contains individual skins");
				wardrobeOpened = true;
				previousPicker = mounts;
				MountClient.showVariants(MountClient.types().get(0));
				return;
			}
			if (wardrobeOpened) {
				var categories = nativeManager().getCategories();
				require(categories.stream().filter(c -> c.getName().equals("Mounts")).count() == 1, "Duplicate Mounts category after refresh");
				var picker = categories.stream().filter(c -> c.getName().equals("Mounts")).findFirst().orElseThrow();
				// Native BaseUI rebuilds categories during rendering, not fetchCategories().
				if (picker == previousPicker) {
					require(++pickerTicks <= 100, "Native wardrobe did not render the requested picker");
					return;
				}
				pickerTicks = 0;
				if (pickerIndex < MountClient.types().size()) {
					String type = MountClient.types().get(pickerIndex++);
					require(picker.getTitle().equals("Mount variants"), "Variant picker has the wrong title");
					require(picker.getEntries().stream().filter(MountElement.class::isInstance).count()
							== MountClient.variants(type).size(), "Variant picker omits skins: " + type);
					require(picker.getEntries().stream().filter(MountBackElement.class::isInstance).count() == 1,
							"Variant picker has no unique Back tile: " + type);
					previousPicker = picker;
					MountClient.showVariants(pickerIndex < MountClient.types().size() ? MountClient.types().get(pickerIndex) : null);
					return;
				}
				require(picker.getTitle().equals("Mounts") && picker.getEntries().size() == MountClient.types().size(),
						"Returning from variants did not restore the species view");
				try {
					Files.writeString(Path.of("mount-client-smoke-passed.txt"),
							"PASS: catalog deferred across actual resource reload; " + MountClient.ids().size() + " actual skin resources; rider/stopped/remote movement "
							+ "AnimationState; player InventoryScreen while mounted; actual wardrobe screen, distinct animal icon, "
							+ "one species category and all variant pickers refreshed by real rendering.\n");
				} catch (java.io.IOException failure) {
					throw new IllegalStateException("Cannot record client smoke result", failure);
				}
				stopAtTick = ticks + 1200;
				verified = true;
				return;
			}
			for (String id : MountClient.ids()) {
				var entity = Registries.ENTITY_TYPE.get(new Identifier(MountService.entityId(id))).create(client.world);
				require(entity instanceof LivingEntity && entity instanceof GeoAnimatable, "Invalid client mount: " + id);
				var horse = (LivingEntity) entity;
				horse.readNbt(MountClient.preview(id).copy());
				var texture = client.getEntityRenderDispatcher().getRenderer(horse).getTexture(horse);
				require(client.getResourceManager().getResource(texture).isPresent(), "Missing skin: " + id);
				String variant = MountClient.preview(id).getString("Variant");
				boolean correctSkin = texture.getPath().endsWith("/" + variant);
				if (MountService.entityId(id).equals("mythicmounts:nightmare")) {
					String file = texture.getPath().substring(texture.getPath().lastIndexOf('/') + 1);
					correctSkin = file.matches(java.util.regex.Pattern.quote(variant.replace(".png", "")) + "_frame[1-4]b?\\.png");
				}
				require(correctSkin,
						"Preview displays the wrong variant: " + id + " -> " + texture);
				client.player.startRiding(horse, true);
				horse.setVelocity(0, 0, 0);
				horse.refreshPositionAndAngles(10, 100, 10, 0, 0);
				horse.prevX = horse.getX();
				horse.prevZ = horse.getZ();
				var event = new AnimationState<>((GeoAnimatable) horse, 0, 0, 0, false);
				require(!event.isMoving(), "Stationary mount walks: " + id);
				horse.prevX -= 0.1;
				require(event.isMoving(), "Rider displacement does not animate: " + id);
				client.player.openRidingInventory();
				require(client.currentScreen instanceof InventoryScreen, "Riding inventory did not open player screen");
				client.setScreen(null);
				client.player.stopRiding();
				require(!event.isMoving(), "Unridden animation behavior changed");
				client.player.startRiding(horse, true);
				horse.prevX = horse.getX();
				require(!event.isMoving(), "Stopped rider kept walking");
				client.player.stopRiding();
			}
			shortcutRequested = true;
			client.getNetworkHandler().sendChatCommand("mounts");
		});
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}

	private static BoxManager nativeManager() {
		try {
			var field = v1_20.morecosmetics.gui.screen.BaseUI.class.getDeclaredField("boxManager");
			field.setAccessible(true);
			Object value = field.get(v1_20.morecosmetics.gui.screen.WardrobeUI.getScreen().getUI());
			if (value instanceof BoxManager manager) return manager;
			throw new IllegalStateException("Native wardrobe manager was not initialized");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Cannot inspect the pinned native wardrobe manager", failure);
		}
	}
}
