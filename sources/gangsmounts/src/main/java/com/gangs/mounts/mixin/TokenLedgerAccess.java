package com.gangs.mounts.mixin;

import java.util.Map;
import java.util.UUID;
import com.thegangs.gangshats.CosmeticTokenState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CosmeticTokenState.class, remap = false)
public interface TokenLedgerAccess {
	@Accessor("tokens")
	Map<UUID, ?> mountTokens();
}
