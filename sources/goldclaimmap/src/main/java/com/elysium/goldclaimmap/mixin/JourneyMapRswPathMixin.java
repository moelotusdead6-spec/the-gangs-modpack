package com.elysium.goldclaimmap.mixin;

import com.elysium.goldclaimmap.RswMapPaths;
import java.io.File;
import java.nio.file.Path;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "journeymap.client.io.FileHandler", remap = false)
public class JourneyMapRswPathMixin {
    @Inject(method = "getDimPath", at = @At("RETURN"), cancellable = true)
    private static void goldclaimmap$rswPath(File worldDir, RegistryKey<World> dimension, CallbackInfoReturnable<Path> ci) {
        ci.setReturnValue(RswMapPaths.dimensionPath(ci.getReturnValue(), dimension.getValue().toString()));
    }
}
