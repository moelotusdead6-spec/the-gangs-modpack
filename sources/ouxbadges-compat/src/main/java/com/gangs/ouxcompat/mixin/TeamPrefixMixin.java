package com.gangs.ouxcompat.mixin;

import com.gangs.ouxcompat.OuxBadgeKeys;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renders rank images directly from the scoreboard team, even when the server badge packet arrives too early. */
@Mixin(Team.class)
public abstract class TeamPrefixMixin {
    @Inject(method = "getPrefix", at = @At("HEAD"), cancellable = true)
    private void gangsOux$useBadgePrefix(CallbackInfoReturnable<Text> callback) {
        Team team = (Team)(Object)this;
        int badge = OuxBadgeKeys.index(team.getName());
        if (badge >= 0) callback.setReturnValue(OuxBadgeKeys.glyphText(badge));
    }
}
