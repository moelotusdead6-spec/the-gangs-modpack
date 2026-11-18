package com.gangs.invhistory.mixin;

import com.gangs.invhistory.InvHistoryMod;
import com.gangs.invhistory.SnapshotEvent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerDeathMixin {
    // HEAD runs before graves, keepInventory handling or vanilla drops empty the inventory.
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void gangsinvhistory$snapshotBeforeDrops(DamageSource source, CallbackInfo ci) {
        var player = (ServerPlayerEntity) (Object) this;
        InvHistoryMod.capture(player, SnapshotEvent.DEATH,
            Text.Serializer.toJson(source.getDeathMessage(player)));
    }
}
