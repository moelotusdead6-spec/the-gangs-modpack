package com.gangs.gangstrials.mixin.backport;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderers.class)
public interface EntityRenderersInvoker {
    @Invoker("register")
    static <T extends Entity> void gangstrials$register(EntityType<? extends T> type, EntityRendererFactory<T> factory) {
        throw new AssertionError();
    }
}
