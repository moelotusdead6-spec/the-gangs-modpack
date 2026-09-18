package com.thegangs.gangshats;

import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class PetService {
	private PetService() {
	}

	public static void despawn(ServerPlayerEntity player) {
		UUID ownerId = player.getUuid();
		for (ServerWorld world : player.getServer().getWorlds()) {
			for (GangPetEntity pet : world.getEntitiesByType(PetEntities.GANG_PET,
					entity -> entity.belongsTo(ownerId))) {
				pet.discard();
			}
		}
	}

	public static void spawn(ServerPlayerEntity player, String rewardId) {
		despawn(player);
		ServerWorld world = player.getServerWorld();
		GangPetEntity pet = PetEntities.GANG_PET.create(world);
		if (pet == null) {
			return;
		}
		pet.initialize(player.getUuid(), rewardId);
		pet.refreshPositionAndAngles(player.getX() + 1.0D, player.getY() + 0.35D, player.getZ() + 1.0D,
				player.getYaw(), 0.0F);
		world.spawnEntity(pet);
	}

	// Re-summons the selected pet after logins, deaths, and dimension changes.
	public static void reconcile(ServerPlayerEntity player) {
		String rewardId = CosmeticUnlockState.get(player.getServer()).selected(player.getUuid(), "pet");
		if (rewardId == null) {
			return;
		}
		UUID ownerId = player.getUuid();
		boolean present = !player.getServerWorld()
				.getEntitiesByType(PetEntities.GANG_PET, entity -> entity.belongsTo(ownerId)).isEmpty();
		if (!present) {
			spawn(player, rewardId);
		}
	}
}
