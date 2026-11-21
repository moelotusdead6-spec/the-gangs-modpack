package com.gangs.gangstrials.smoke.mixin;

import com.gangs.gangstrials.smoke.BootsTrialsSmoke;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SinglePoolElement.class)
public abstract class SinglePoolElementMixin {
    @Inject(method = "generate", at = @At("HEAD"))
    private void gangsSmoke$recordIntendedSpawners(StructureTemplateManager manager, StructureWorldAccess world,
            StructureAccessor accessor, ChunkGenerator generator, BlockPos pos, BlockPos pivot,
            BlockRotation rotation, BlockBox box, Random random, boolean keepJigsaws,
            CallbackInfoReturnable<Boolean> cir) {
        if (!BootsTrialsSmoke.collectExpectedSpawners) return;
        for (var info : ((SinglePoolElement) (Object) this).getStructureBlockInfos(manager, pos, rotation, Random.create(400))) {
            if (info.nbt() != null && info.nbt().getString("target").equals("minecraft:spawner_connector")
                    && info.nbt().getString("pool").startsWith("minecraft:trial_chambers/spawner/")) {
                BootsTrialsSmoke.expectedSpawners.add(info.pos().down());
            }
        }
    }
}
