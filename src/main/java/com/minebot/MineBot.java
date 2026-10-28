package com.minebot;

import com.minebot.commands.BotCommands;
import com.minebot.networking.BotNetworking;
import com.minebot.registry.ModBlocks;
import com.minebot.registry.ModItems;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineBot implements ModInitializer {
	public static final String MOD_ID = "minebot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		ModBlocks.init();
		BotNetworking.init();
		BotCommands.init();
		LOGGER.info("[MineBot] initialized: mining machine + bot GUI ready");
	}
}
