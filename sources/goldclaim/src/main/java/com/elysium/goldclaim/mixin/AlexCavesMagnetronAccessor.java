package com.elysium.goldclaim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.living.MagnetronEntity", remap = false)
public interface AlexCavesMagnetronAccessor {
    @Accessor("droppedHeart")
    boolean goldclaim$hasDroppedHeart();
}
