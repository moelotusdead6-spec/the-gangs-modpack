package com.elysium.goldclaim.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.boss.dragon.EnderDragonSpawnState;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(EnderDragonFight.class)
public interface EnderDragonFightInvoker {
    @Accessor("dragonSpawnState")
    EnderDragonSpawnState goldclaim$getDragonSpawnState();

    @Accessor("dragonSpawnState")
    void goldclaim$setDragonSpawnState(EnderDragonSpawnState state);

    @Accessor("spawnStateTimer")
    void goldclaim$setSpawnStateTimer(int timer);

    @Accessor("crystals")
    void goldclaim$setCrystals(List<EndCrystalEntity> crystals);

    @Invoker("generateEndPortal")
    void goldclaim$generateEndPortal(boolean open);
}
