package com.elysium.goldclaim.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.Set;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArgumentCommandNode.class, remap = false)
public abstract class PlayerNameSuggestionsMixin {
    @Shadow @Final private ArgumentType<?> type;
    @Shadow @Final @Mutable private SuggestionProvider<ServerCommandSource> customSuggestions;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void goldclaim$completePlayerNames(CallbackInfo ci) {
        String name = ((ArgumentCommandNode<?, ?>)(Object)this).getName();
        if (customSuggestions == null && type instanceof StringArgumentType string
                && string.getType() == StringArgumentType.StringType.SINGLE_WORD
                && Set.of("player", "playerName", "username", "recipient", "owner").contains(name)) {
            customSuggestions = (context, builder) ->
                CommandSource.suggestMatching(context.getSource().getPlayerNames(), builder);
        }
    }
}
