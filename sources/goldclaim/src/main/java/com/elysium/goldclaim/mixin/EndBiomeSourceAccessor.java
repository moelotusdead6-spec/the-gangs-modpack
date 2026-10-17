package com.elysium.goldclaim.mixin;

import org.betterx.bclib.interfaces.BiomeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "org.betterx.bclib.api.v2.generator.BCLibEndBiomeSource", remap = false)
public interface EndBiomeSourceAccessor {
    @Accessor("mapLand")
    BiomeMap goldclaim$landMap();

    @Accessor("mapVoid")
    void goldclaim$setVoidMap(BiomeMap map);

    @Accessor("mapBarrens")
    void goldclaim$setBarrensMap(BiomeMap map);
}
