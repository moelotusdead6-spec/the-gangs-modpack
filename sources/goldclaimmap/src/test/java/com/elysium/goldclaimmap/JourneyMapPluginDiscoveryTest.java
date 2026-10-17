package com.elysium.goldclaimmap;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JourneyMapPluginDiscoveryTest {
    @Test
    public void brokenLegacyPluginDoesNotPreventValidPluginsLoading() {
        EntrypointContainer<String> broken = entry("waystones", () -> {
            throw new EntrypointException("journeymap", "waystones",
                    new NoClassDefFoundError("journeymap/client/api/IClientPlugin"));
        });
        assertEquals(List.of("before", "goldclaimmap"), JourneyMapPluginDiscovery.load(List.of(
                entry("before", () -> "before"), broken, entry("goldclaimmap", () -> "goldclaimmap"))));
    }

    @Test
    public void directLinkageFailureIsIsolatedToo() {
        EntrypointContainer<String> broken = entry("legacy", () -> {
            throw new NoClassDefFoundError("journeymap/client/api/IClientPlugin");
        });
        assertEquals(List.of("goldclaimmap"), JourneyMapPluginDiscovery.load(List.of(
                broken, entry("goldclaimmap", () -> "goldclaimmap"))));
    }

    @Test(expected = IllegalStateException.class)
    public void unexpectedDiscoveryBugsAreNotHidden() {
        JourneyMapPluginDiscovery.load(List.of(entry("bug", () -> {
            throw new IllegalStateException("unexpected");
        })));
    }

    private static <T> EntrypointContainer<T> entry(String modId, Supplier<T> factory) {
        ModMetadata metadata = (ModMetadata) Proxy.newProxyInstance(
                ModMetadata.class.getClassLoader(), new Class<?>[]{ModMetadata.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getId")) {
                        return modId;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        ModContainer provider = (ModContainer) Proxy.newProxyInstance(
                ModContainer.class.getClassLoader(), new Class<?>[]{ModContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getMetadata")) {
                        return metadata;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new EntrypointContainer<>() {
            @Override
            public T getEntrypoint() {
                return factory.get();
            }

            @Override
            public ModContainer getProvider() {
                return provider;
            }
        };
    }
}
