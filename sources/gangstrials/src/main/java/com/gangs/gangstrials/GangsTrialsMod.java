package com.gangs.gangstrials;

import net.fabricmc.api.ModInitializer;

public final class GangsTrialsMod implements ModInitializer {
    @Override
    public void onInitialize() {
        StructureDimensionRules.load();
    }
}
