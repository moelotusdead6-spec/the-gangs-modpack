package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.StaffLocatePolicy;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocateCommand.class)
public abstract class StaffLocateMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private static void goldclaim$staffLocate(CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess, CallbackInfo ci) {
        var locate = dispatcher.getRoot().getChild("locate");
        if (locate == null) throw new IllegalStateException("Vanilla /locate registration was suppressed.");
        ((CommandNodeAccessor) locate).goldclaim$setRequirement(StaffLocatePolicy::canUse);
    }
}
