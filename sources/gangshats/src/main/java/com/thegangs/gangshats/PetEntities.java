package com.thegangs.gangshats;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PetEntities {
	public static final EntityType<GangPetEntity> GANG_PET = Registry.register(Registries.ENTITY_TYPE,
			new Identifier(GangsHats.MOD_ID, "gang_pet"),
			FabricEntityTypeBuilder.create(SpawnGroup.MISC, GangPetEntity::new)
					.dimensions(EntityDimensions.fixed(0.5F, 0.5F))
					.trackRangeBlocks(32)
					.build());

	private PetEntities() {
	}

	public static void register() {
		GANG_PET.getTranslationKey();
	}
}
