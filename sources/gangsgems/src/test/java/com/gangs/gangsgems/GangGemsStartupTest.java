package com.gangs.gangsgems;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GangGemsStartupTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void startsDiscordListenerWhenFabricBridgeIsInstalled() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getSavePath(WorldSavePath.ROOT)).thenReturn(folder.getRoot().toPath());
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(folder.getRoot().toPath());
        when(loader.isModLoaded("dcintegration-fabric")).thenReturn(true);
        try (var fabric = mockStatic(FabricLoader.class);
             var discord = mockStatic(DiscordOrderBridge.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            GangGems.start(server);
            discord.verify(() -> DiscordOrderBridge.start(eq(server), any(WalletStore.class), eq(false), any(Runnable.class)));
        }
    }

    @Test
    public void retriesUntilDiscordBotIsReady() throws Exception {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.getSavePath(WorldSavePath.ROOT)).thenReturn(folder.getRoot().toPath());
        FabricLoader loader = mock(FabricLoader.class);
        when(loader.getConfigDir()).thenReturn(folder.getRoot().toPath());
        when(loader.isModLoaded("dcintegration-fabric")).thenReturn(true);
        try (var fabric = mockStatic(FabricLoader.class);
             var discord = mockStatic(DiscordOrderBridge.class)) {
            fabric.when(FabricLoader::getInstance).thenReturn(loader);
            discord.when(() -> DiscordOrderBridge.start(eq(server), any(WalletStore.class), eq(false), any(Runnable.class)))
                .thenThrow(new DiscordOrderBridge.NotReadyException())
                .thenReturn(mock(DiscordOrderBridge.class));
            GangGems.start(server);
            for (int tick = 0; tick < 200; tick++) GangGems.retryDiscord(server);
            discord.verify(() -> DiscordOrderBridge.start(eq(server), any(WalletStore.class), eq(false), any(Runnable.class)),
                times(2));
        }
    }
}
