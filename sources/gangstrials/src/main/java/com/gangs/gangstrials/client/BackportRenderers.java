package com.gangs.gangstrials.client;

import com.drd.trickytrialsbackport.registry.ModEntities;
import com.gangs.gangstrials.mixin.backport.EntityRenderersInvoker;

final class BackportRenderers {
    private BackportRenderers() {
    }

    static void register() {
        EntityRenderersInvoker.gangstrials$register(ModEntities.OMINOUS_ITEM_SPAWNER.get(), OminousItemSpawnerRenderer::new);
    }
}
