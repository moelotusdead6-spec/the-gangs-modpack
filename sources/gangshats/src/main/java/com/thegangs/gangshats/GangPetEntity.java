package com.thegangs.gangshats;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class GangPetEntity extends Entity {
	private static final TrackedData<String> REWARD_ID = DataTracker.registerData(GangPetEntity.class,
			TrackedDataHandlerRegistry.STRING);
	private UUID ownerId;

	public GangPetEntity(EntityType<? extends GangPetEntity> type, World world) {
		super(type, world);
	}

	public void initialize(UUID ownerId, String rewardId) {
		this.ownerId = ownerId;
		this.dataTracker.set(REWARD_ID, rewardId);
	}

	public String getRewardId() {
		return dataTracker.get(REWARD_ID);
	}

	public boolean belongsTo(UUID playerId) {
		return playerId.equals(ownerId);
	}

	@Override
	protected void initDataTracker() {
		dataTracker.startTracking(REWARD_ID, "gangshats:pet_ghost");
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld serverWorld) || ownerId == null) {
			return;
		}
		ServerPlayerEntity owner = serverWorld.getServer().getPlayerManager().getPlayer(ownerId);
		if (owner == null || owner.getWorld() != serverWorld) {
			return;
		}
		if (squaredDistanceTo(owner) > 144.0D) {
			refreshPositionAndAngles(owner.getX() + 1.0D, owner.getY() + 0.35D, owner.getZ() + 1.0D, owner.getYaw(), 0.0F);
		} else {
			setPosition(owner.getX() + 1.0D, owner.getY() + 0.35D, owner.getZ() + 1.0D);
		}
	}

	private void savePetData(NbtCompound nbt) {
		if (ownerId != null) {
			nbt.putUuid("Owner", ownerId);
		}
		nbt.putString("RewardId", getRewardId());
	}

	private void loadPetData(NbtCompound nbt) {
		if (nbt.containsUuid("Owner")) {
			ownerId = nbt.getUuid("Owner");
		}
		if (nbt.contains("RewardId")) {
			dataTracker.set(REWARD_ID, nbt.getString("RewardId"));
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		loadPetData(nbt);
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		savePetData(nbt);
	}
}
