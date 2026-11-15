package com.gangs.mounts.mixin;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.yahoo.chirpycricket.mythicmounts.entity.MountEntity", remap = false)
public interface MountDataAccess {
	@Accessor("textures")
	ArrayList<String> mountTextures();
}
