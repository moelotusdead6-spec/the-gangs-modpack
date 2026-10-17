package com.elysium.goldclaimmap;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JourneyMapPluginDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("goldclaimmap");

    private JourneyMapPluginDiscovery() {
    }

    public static <T> List<T> load(List<EntrypointContainer<T>> containers) {
        List<T> plugins = new ArrayList<>();
        for (EntrypointContainer<T> container : containers) {
            try {
                plugins.add(container.getEntrypoint());
            } catch (EntrypointException | LinkageError e) {
                LOGGER.warn("Skipping incompatible JourneyMap plugin from {}; other plugins will still load",
                        container.getProvider().getMetadata().getId(), e);
            }
        }
        return plugins;
    }
}
