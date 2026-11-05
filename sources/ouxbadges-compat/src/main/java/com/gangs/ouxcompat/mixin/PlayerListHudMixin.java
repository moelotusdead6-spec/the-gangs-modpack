package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.OuxBadgeKeys;
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

/** Keeps the Rank Badges tab order (rank before game mode). Also gives CompatMixinPlugin its PlayerListHud hook. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Shadow @Final @Mutable
    private static Comparator<PlayerListEntry> ENTRY_ORDERING;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void gangsOux$rankBeforeGameMode(CallbackInfo callback) {
        Comparator<PlayerListEntry> vanilla = ENTRY_ORDERING;
        ENTRY_ORDERING = Comparator.comparingInt((PlayerListEntry entry) ->
            OuxBadgeKeys.priority(entry.getScoreboardTeam() == null ? null : entry.getScoreboardTeam().getName()))
            .thenComparing(vanilla);
    }
}
