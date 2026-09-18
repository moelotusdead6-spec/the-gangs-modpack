package com.thegangs.gangshats.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientCosmeticState {
	private static final Map<UUID, Map<String, String>> SELECTED = new HashMap<>();

	private ClientCosmeticState() {
	}

	public static void select(UUID playerId, String slot, String rewardId) {
		SELECTED.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(slot, rewardId);
	}

	public static String selected(UUID playerId, String slot) {
		return SELECTED.getOrDefault(playerId, Map.of()).get(slot);
	}
}
