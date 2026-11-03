package com.elysium.goldclaim;

import dev.fiw.modsapi.FiwModsApi;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

final class LoginVerification {
    private LoginVerification() {}

    static boolean isPending(UUID playerId) {
        if (!FabricLoader.getInstance().isModLoaded("fiw-mods-api")) return false;
        var engine = FiwModsApi.engine();
        if (engine == null) {
            throw new IllegalStateException("Fiw is loaded but its verification engine is not initialized");
        }
        return engine.freezes().isFrozen(playerId);
    }
}
