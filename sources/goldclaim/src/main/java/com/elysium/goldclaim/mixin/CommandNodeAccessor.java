/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.mojang.brigadier.tree.CommandNode
 *  net.minecraft.server.command.ServerCommandSource
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.elysium.goldclaim.mixin;

import com.mojang.brigadier.tree.CommandNode;
import java.util.function.Predicate;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={CommandNode.class}, remap=false)
public interface CommandNodeAccessor {
    @Mutable
    @Accessor(value="requirement", remap=false)
    public void goldclaim$setRequirement(Predicate<ServerCommandSource> var1);
}
