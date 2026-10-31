package com.gangs.rankbadges.mixin;

import com.gangs.rankbadges.TabOrder;
import java.util.Comparator;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Shadow @Final @Mutable
    private static Comparator<PlayerListEntry> ENTRY_ORDERING;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void rankbadges$rankBeforeGameMode(CallbackInfo callback) {
        Comparator<PlayerListEntry> vanilla = ENTRY_ORDERING;
        ENTRY_ORDERING = Comparator.comparingInt((PlayerListEntry entry) ->
            entry.getScoreboardTeam() == null ? 4 : TabOrder.priority(entry.getScoreboardTeam().getName()))
            .thenComparing(vanilla);
    }
}
