package com.gangs.gangsgems.display;

import com.gangs.gangsgems.GemDisplayServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GemDisplayClient implements ClientModInitializer {
    private static boolean enabled;
    private static boolean available;
    private static long balance;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(GemDisplayServer.CHANNEL, (client, handler, buffer, sender) -> {
            boolean active = buffer.readBoolean();
            boolean ready = active && buffer.readBoolean();
            long amount = ready ? buffer.readLong() : 0;
            client.execute(() -> {
                enabled = active;
                available = ready;
                balance = amount;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            enabled = false;
            available = false;
            balance = 0;
        });
    }

    public static boolean enabled() { return enabled; }
    public static boolean available() { return available; }
    public static long balance() { return balance; }
}
