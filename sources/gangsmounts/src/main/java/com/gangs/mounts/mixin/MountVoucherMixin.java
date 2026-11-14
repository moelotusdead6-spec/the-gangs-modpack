package com.gangs.mounts.mixin;

import com.gangs.mounts.MountService;
import com.thegangs.gangshats.CosmeticTokenState;
import com.thegangs.gangshats.CosmeticUnlockState;
import com.thegangs.gangshats.GangsHats;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GangsHats.class, remap = false)
public abstract class MountVoucherMixin {
	@Inject(method = "redeemVoucher", at = @At("HEAD"), cancellable = true)
	private static void retainOwnedMountVoucher(ServerPlayerEntity player, ItemStack voucher,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> ci) {
		var nbt = voucher.getNbt();
		if (nbt == null || !nbt.containsUuid("gangscosmetics_token_id")
				|| !nbt.getString("gangscosmetics_token_kind").equals("voucher")) return;
		Object ledger = CosmeticTokenState.get(player.getServer());
		if (!(ledger instanceof TokenLedgerAccess access)) throw new IllegalStateException("Token ledger mixin missing");
		Object record = access.mountTokens().get(nbt.getUuid("gangscosmetics_token_id"));
		if (record instanceof TokenRecordAccess token && token.mountKind().equals("voucher")
				&& MountService.contains(token.mountReward())
				&& CosmeticUnlockState.get(player.getServer()).hasUnlock(player.getUuid(), token.mountReward())) {
			player.sendMessage(Text.literal("You already own this mount. Trade the voucher to someone else."), false);
			ci.setReturnValue(TypedActionResult.fail(voucher));
		}
	}
}
