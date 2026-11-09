package com.gangs.botaniacompat;

import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;

public final class BotaniaCompat implements ModInitializer {
    public static final String TIARA = "botania:flight_tiara";
    public static final String NOTICE = "Botania Tiara flight, gliding and aerial dash are disabled on this server.";

    @Override
    public void onInitialize() {
        LoggerFactory.getLogger("Gangs/BotaniaCompat").info(NOTICE);
    }
}
