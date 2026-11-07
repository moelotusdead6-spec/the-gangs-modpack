package com.gangs.gangstrials.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class GangsTrialsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("trickytrialsbackport")) {
            BackportRenderers.register();
        }
    }
}
