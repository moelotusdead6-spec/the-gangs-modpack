package com.elysium.goldclaimmap.mixin;

import com.elysium.goldclaimmap.RswMapPaths;
import java.io.File;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "journeymap.client.model.region.RegionCoord", remap = false)
public class JourneyMapRswRegionMixin {
    @Shadow @Final @Mutable public File worldDir;
    @Shadow @Final public RegistryKey<World> dimension;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void goldclaimmap$pinRswRegion(CallbackInfo ci) {
        this.worldDir = RswMapPaths.worldRoot(this.worldDir.toPath(), this.dimension.getValue().toString()).toFile();
    }
}
