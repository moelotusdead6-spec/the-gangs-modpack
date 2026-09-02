/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.network.ServerPlayerEntity
 */
package com.gangs.gangshop.economy;

import com.gangs.gangshop.economy.WalletStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;

public class GangBucksService {
    private final WalletStore walletStore;

    public GangBucksService(WalletStore walletStore) {
        this.walletStore = walletStore;
    }

    public long getBalance(ServerPlayerEntity player) {
        return this.walletStore.getBalance(player.getUuid());
    }

    public long getBalance(UUID playerId) {
        return this.walletStore.getBalance(playerId);
    }

    public void add(ServerPlayerEntity player, long amount) {
        this.walletStore.addBalance(player.getUuid(), amount);
    }

    public void add(UUID playerId, long amount) {
        this.walletStore.addBalance(playerId, amount);
    }

    public boolean subtract(ServerPlayerEntity player, long amount) {
        return this.walletStore.subtractBalance(player.getUuid(), amount);
    }

    public boolean subtract(UUID playerId, long amount) {
        return this.walletStore.subtractBalance(playerId, amount);
    }

    public void set(ServerPlayerEntity player, long amount) {
        this.walletStore.setBalance(player.getUuid(), amount);
    }

    public void set(UUID playerId, long amount) {
        this.walletStore.setBalance(playerId, amount);
    }

    public boolean grantStarterIfAbsent(ServerPlayerEntity player, long amount) {
        return this.walletStore.grantStarterIfAbsent(player.getUuid(), amount);
    }

    public List<Map.Entry<UUID, Long>> topBalances(int limit) {
        return this.walletStore.topBalances(limit);
    }
}

