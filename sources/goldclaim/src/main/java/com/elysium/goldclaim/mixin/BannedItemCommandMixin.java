package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.BannedContent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStackArgument.class)
public abstract class BannedItemCommandMixin {
    @Shadow public abstract Item getItem();

    @Inject(method = "createStack", at = @At("HEAD"))
    private void goldclaim$rejectBinCommand(int count, boolean checkOverstack,
                                           CallbackInfoReturnable<ItemStack> cir) throws CommandSyntaxException {
        if (BannedContent.isBanned(Registries.ITEM.getId(this.getItem()))) {
            BannedContent.report("item command rejected");
            throw new SimpleCommandExceptionType(Text.literal("The furniture bin is disabled on this server.")).create();
        }
    }
}
