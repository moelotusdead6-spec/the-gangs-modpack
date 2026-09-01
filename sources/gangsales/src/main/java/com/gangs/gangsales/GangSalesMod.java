package com.gangs.gangsales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class GangSalesMod implements ModInitializer {
	public static final String MOD_ID = "gangsales";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final SalesStore SALES = new SalesStore();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(GangSalesCommands::register);
		ServerLifecycleEvents.SERVER_STARTED.register(SALES::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SALES.save());
	}
}