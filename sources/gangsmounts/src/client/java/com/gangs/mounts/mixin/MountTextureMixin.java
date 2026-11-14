package com.gangs.mounts.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "software.bernie.geckolib.renderer.GeoEntityRenderer", remap = false)
public abstract class MountTextureMixin {
	// Mythic Mounts 7.2's old getTexture overload is not the GeckoLib 4.8 entity texture override.
	@Inject(method = {
			"getTexture(Lnet/minecraft/entity/Entity;)Lnet/minecraft/util/Identifier;",
			"method_3931(Lnet/minecraft/class_1297;)Lnet/minecraft/class_2960;"
	}, at = @At("HEAD"), cancellable = true)
	private void useMountTexture(Entity entity, CallbackInfoReturnable<Identifier> ci) {
		if (!Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("mythicmounts")) return;
		try {
			String path = (String) entity.getClass().getMethod("getTextureFile").invoke(entity);
			if (path.equals("textures/model/entity/")) {
				path = (String) entity.getClass().getMethod("getDefaultTexture").invoke(entity);
			}
			ci.setReturnValue(new Identifier("mythicmounts", path));
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Cannot resolve Mythic Mounts texture for " + entity.getType(), failure);
		}
	}
}
