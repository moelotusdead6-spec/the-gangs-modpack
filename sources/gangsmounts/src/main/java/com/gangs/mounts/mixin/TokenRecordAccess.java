package com.gangs.mounts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.thegangs.gangshats.CosmeticTokenState$TokenRecord", remap = false)
public interface TokenRecordAccess {
	@Accessor("kind")
	String mountKind();
	@Accessor("rewardId")
	String mountReward();
}
