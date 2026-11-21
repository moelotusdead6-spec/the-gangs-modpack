package com.elysium.goldclaim.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gangs Cosmetics only redeems keys minted by /cosmetickey. Keys from /give, the creative tab or
 * staff mailbox sends carry no ledger token, so treat them as fresh staff-issued keys. Tokenized
 * keys still go through the ledger, so duplicated or already-redeemed keys stay rejected.
 */
@Pseudo
@Mixin(targets = "com.thegangs.gangshats.CosmeticTokenState", remap = false)
public abstract class CosmeticKeyTokenMixin {
    @Unique
    private static final Logger GOLDCLAIM$LOGGER = LoggerFactory.getLogger("GoldClaim/CosmeticKeys");

    @Inject(method = "consumeKey", at = @At("HEAD"), cancellable = true, require = 0)
    private void goldclaim$acceptStaffIssuedKey(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.containsUuid("gangscosmetics_token_id")) {
            GOLDCLAIM$LOGGER.info("Redeeming untokenized (staff-issued) Cosmetic Key.");
            cir.setReturnValue(true);
        }
    }
}
