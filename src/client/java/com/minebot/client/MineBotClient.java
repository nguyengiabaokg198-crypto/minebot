package com.minebot.client;

import com.minebot.networking.BotStatePayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class MineBotClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(BotStatePayload.TYPE, (payload, ctx) ->
			ctx.client().execute(() -> BotPanelScreen.handleState(payload)));
	}
}
