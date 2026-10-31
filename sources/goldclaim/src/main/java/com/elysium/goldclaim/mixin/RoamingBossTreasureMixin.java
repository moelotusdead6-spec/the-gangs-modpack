package com.elysium.goldclaim.mixin;

import com.brutalbosses.entity.capability.BossCapEntity;
import com.brutalbosses.event.EventHandler;
import com.elysium.goldclaim.RswRoamingBosses;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.brutalbosses.entity.BossType", remap = false)
public abstract class RoamingBossTreasureMixin {
    @WrapMethod(method = "initForEntity")
    private void goldclaim$preserveTreasureProtection(LivingEntity entity, Operation<Void> original) {
        if (!entity.getCommandTags().contains(RswRoamingBosses.TAG)) {
            original.call(entity);
            return;
        }
        // Boss initialization also runs when saved entities load, not just at natural spawn.
        var pos = ((BossCapEntity)entity).getBossCap().getSpawnPos();
        var previous = EventHandler.protectedBlocks.get(pos);
        try {
            original.call(entity);
        } finally {
            if (previous == null) EventHandler.protectedBlocks.remove(pos, entity.getUuid());
            else EventHandler.protectedBlocks.put(pos, previous);
        }
    }
}
