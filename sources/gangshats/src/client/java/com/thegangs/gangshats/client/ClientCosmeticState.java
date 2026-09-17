package com.thegangs.gangshats.client;

import java.util.HashMap;
import java.util.Map;

public final class ClientCosmeticState {
	private static final Map<String, String> SELECTED = new HashMap<>();

	private ClientCosmeticState() {
	}

	public static void select(String slot, String rewardId) {
		SELECTED.put(slot, rewardId);
	}

	public static String selected(String slot) {
		return SELECTED.get(slot);
	}
}
