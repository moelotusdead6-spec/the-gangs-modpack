package com.gangs.gangstrials.smoke.mixin;

import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface WorldRandomAccessor {
    @Mutable
    @Accessor("random")
    void gangsSmoke$setRandom(Random random);
}
