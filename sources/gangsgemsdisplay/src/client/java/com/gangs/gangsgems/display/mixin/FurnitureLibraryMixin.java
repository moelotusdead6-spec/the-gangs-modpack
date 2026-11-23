package com.gangs.gangsgems.display.mixin;

import com.gangs.gangsgems.display.GemDisplayClient;
import java.util.List;
import net.conczin.immersive_furniture.client.gui.ArtisansWorkstationLibraryScreen;
import net.conczin.immersive_furniture.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ArtisansWorkstationLibraryScreen.class, remap = false)
public abstract class FurnitureLibraryMixin {
    private static final Identifier GEM_ICON = new Identifier("gangsgemsdisplay", "textures/gui/gem.png");

    @Redirect(method = "render", remap = true,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItemWithoutEntity(Lnet/minecraft/item/ItemStack;II)V"))
    private void gangsgemsdisplay$drawCurrency(DrawContext context, ItemStack stack, int x, int y) {
        if (!GemDisplayClient.enabled() || !stack.isOf(Items.CRAFTING_MATERIAL)) {
            context.drawItemWithoutEntity(stack, x, y);
            return;
        }
        context.drawTexture(GEM_ICON, x, y, 0, 0, 16, 16, 16, 16);
        var renderer = MinecraftClient.getInstance().textRenderer;
        Text balance = balanceText();
        context.drawTextWithShadow(renderer, balance, x + 16 - renderer.getWidth(balance), y - 11, 0xffffff);
    }

    @Redirect(method = "render", remap = true,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getTooltip(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/client/item/TooltipContext;)Ljava/util/List;"))
    private List<Text> gangsgemsdisplay$currencyTooltip(ItemStack stack, PlayerEntity player, TooltipContext context) {
        if (GemDisplayClient.enabled() && stack.isOf(Items.CRAFTING_MATERIAL)) {
            return List.of(Text.translatable("gangsgemsdisplay.currency"),
                balanceText());
        }
        return stack.getTooltip(player, context);
    }

    private static Text balanceText() {
        return GemDisplayClient.available()
            ? Text.translatable("gangsgemsdisplay.balance", GemDisplayClient.balance())
            : Text.translatable("gangsgemsdisplay.balance_unavailable");
    }
}
