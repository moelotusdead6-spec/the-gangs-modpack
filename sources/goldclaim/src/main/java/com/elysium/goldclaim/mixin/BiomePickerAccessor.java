package com.elysium.goldclaim.mixin;

import java.util.List;
import org.betterx.bclib.api.v2.generator.BiomePicker;
import org.betterx.bclib.util.WeighTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "org.betterx.bclib.api.v2.generator.BiomePicker", remap = false)
public interface BiomePickerAccessor {
    @Accessor("biomes")
    List<BiomePicker.ActualBiome> goldclaim$biomes();

    @Accessor("tree")
    void goldclaim$setTree(WeighTree<BiomePicker.ActualBiome> tree);
}
