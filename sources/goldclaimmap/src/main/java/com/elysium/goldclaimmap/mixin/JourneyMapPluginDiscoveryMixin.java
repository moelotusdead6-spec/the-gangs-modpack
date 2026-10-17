package com.elysium.goldclaimmap.mixin;

import com.elysium.goldclaimmap.JourneyMapPluginDiscovery;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "journeymap.common.helpers.FabricClientHooks", remap = false)
public class JourneyMapPluginDiscoveryMixin {
    // JourneyMap beta.5's bulk lookup aborts every plugin when one uses the old API.
    @Redirect(method = "getClientPluginScanResult", at = @At(value = "INVOKE",
            target = "Lnet/fabricmc/loader/api/FabricLoader;getEntrypoints(Ljava/lang/String;Ljava/lang/Class;)Ljava/util/List;"))
    private <T> List<T> goldclaimmap$loadPluginsIndividually(FabricLoader loader, String key, Class<T> type) {
        return JourneyMapPluginDiscovery.load(loader.getEntrypointContainers(key, type));
    }
}
