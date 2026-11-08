package com.gangs.chefcompat.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.redstonegames.chefsdelight.villager.ModVillagers", remap = false)
public abstract class ChefTradeMixin {
    @Inject(method = "lambda$registerTrades$57", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyBakerPieCrust(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("pie_crust", 4, 1, 10));
    }

    @Inject(method = "lambda$registerTrades$42", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyPumpkinSlice(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("pumpkin_slice", 12, 1, 10));
    }

    @Inject(method = "lambda$registerTrades$27", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyBacon(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("bacon", 4, 1, 3));
    }

    @Inject(method = "lambda$registerTrades$23", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buySalmonRoll(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("salmon_roll", 4, 2, 10));
    }

    @Inject(method = "lambda$registerTrades$22", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyKelpRoll(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("kelp_roll", 3, 2, 10));
    }

    @Inject(method = "lambda$registerTrades$17", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyRawPasta(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("raw_pasta", 9, 1, 10));
    }

    @Inject(method = "lambda$registerTrades$16", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyWheatDough(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("wheat_dough", 9, 1, 10));
    }

    @Inject(method = "lambda$registerTrades$11", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyCookPieCrust(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("pie_crust", 3, 2, 12));
    }

    @Inject(method = "lambda$registerTrades$10", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gangs$buyRice(Entity entity, Random random, CallbackInfoReturnable<TradeOffer> result) {
        result.setReturnValue(gangs$buy("rice", 9, 2, 12));
    }

    @Unique
    private static TradeOffer gangs$buy(String path, int amount, int emeralds, int experience) {
        Identifier id = new Identifier("farmersdelight", path);
        Item item = Registries.ITEM.getOrEmpty(id)
                .orElseThrow(() -> new IllegalStateException("Missing Chef's Delight trade item: " + id));
        return new TradeOffer(new ItemStack(item, amount), new ItemStack(Items.EMERALD, emeralds),
                12, experience, 0.05F);
    }
}
