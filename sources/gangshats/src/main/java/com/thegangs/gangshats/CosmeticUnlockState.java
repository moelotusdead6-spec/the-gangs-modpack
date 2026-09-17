package com.thegangs.gangshats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.PersistentState;

public final class CosmeticUnlockState extends PersistentState {
	private static final String STATE_ID = "gangshats_cosmetic_unlocks";
	private final Map<UUID, Set<String>> unlocked = new HashMap<>();
	private final Map<UUID, Map<String, String>> selected = new HashMap<>();

	public static CosmeticUnlockState fromNbt(NbtCompound nbt) {
		CosmeticUnlockState state = new CosmeticUnlockState();
		NbtList players = nbt.getList("Players", NbtCompound.COMPOUND_TYPE);
		for (int index = 0; index < players.size(); index++) {
			NbtCompound playerNbt = players.getCompound(index);
			UUID playerId = playerNbt.getUuid("Uuid");
			Set<String> rewards = new HashSet<>();
			NbtList rewardList = playerNbt.getList("Rewards", NbtCompound.STRING_TYPE);
			for (int rewardIndex = 0; rewardIndex < rewardList.size(); rewardIndex++) {
				rewards.add(rewardList.getString(rewardIndex));
			}
			state.unlocked.put(playerId, rewards);
			NbtCompound selectedNbt = playerNbt.getCompound("Selected");
			Map<String, String> selectedRewards = new HashMap<>();
			for (String slot : selectedNbt.getKeys()) {
				selectedRewards.put(slot, selectedNbt.getString(slot));
			}
			state.selected.put(playerId, selectedRewards);
		}
		return state;
	}

	public boolean unlock(UUID playerId, String rewardId) {
		boolean added = unlocked.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(rewardId);
		if (added) {
			markDirty();
		}
		return added;
	}

	public boolean hasUnlock(UUID playerId, String rewardId) {
		return unlocked.getOrDefault(playerId, Set.of()).contains(rewardId);
	}

	public void select(UUID playerId, String slot, String rewardId) {
		selected.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(slot, rewardId);
		markDirty();
	}

	public String selected(UUID playerId, String slot) {
		return selected.getOrDefault(playerId, Map.of()).get(slot);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList players = new NbtList();
		unlocked.forEach((playerId, rewards) -> {
			NbtCompound playerNbt = new NbtCompound();
			playerNbt.putUuid("Uuid", playerId);
			NbtList rewardList = new NbtList();
			rewards.forEach(reward -> rewardList.add(net.minecraft.nbt.NbtString.of(reward)));
			playerNbt.put("Rewards", rewardList);
			NbtCompound selectedNbt = new NbtCompound();
			selected.getOrDefault(playerId, Map.of()).forEach(selectedNbt::putString);
			playerNbt.put("Selected", selectedNbt);
			players.add(playerNbt);
		});
		nbt.put("Players", players);
		return nbt;
	}

	public static CosmeticUnlockState get(net.minecraft.server.world.ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(CosmeticUnlockState::fromNbt,
				CosmeticUnlockState::new, STATE_ID);
	}
}
