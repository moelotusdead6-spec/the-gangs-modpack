package com.elysium.goldclaim.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.EnderDragonSpawnState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EnderDragonFight.class)
public interface EnderDragonFightInvoker {
    @Accessor("dragonSpawnState")
    EnderDragonSpawnState goldclaim$getDragonSpawnState();

    @Invoker("generateEndPortal")
    void goldclaim$generateEndPortal(boolean open);
}
