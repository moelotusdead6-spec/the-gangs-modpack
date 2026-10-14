package com.elysium.goldclaim.mixin;

import com.elysium.goldclaim.LegacyDimensionPolicy;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.TeleportCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeleportCommand.class)
public abstract class LegacyTeleportCommandMixin {
    @Inject(method = "teleport", at = @At("HEAD"))
    private static void goldclaim$rejectClosedDestination(ServerCommandSource source, Entity entity,
            ServerWorld destination, double x, double y, double z, Set<PositionFlag> flags,
            float yaw, float pitch, @Coerce Object lookTarget, CallbackInfo ci) throws CommandSyntaxException {
        if (entity instanceof ServerPlayerEntity && LegacyDimensionPolicy.isClosed(destination)) {
            throw new SimpleCommandExceptionType(Text.literal(LegacyDimensionPolicy.MESSAGE)).create();
        }
    }
}
