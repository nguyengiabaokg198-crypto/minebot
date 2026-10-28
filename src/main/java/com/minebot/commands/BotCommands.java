package com.minebot.commands;

import com.minebot.MineBot;
import com.minebot.edit.BotSelfTest;
import com.minebot.registry.ModBlocks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

public final class BotCommands {
	private BotCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(Commands.literal("minebot")
				.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
				.then(Commands.literal("kit").executes(ctx -> giveKit(ctx.getSource())))
				.then(Commands.literal("debugtest").executes(ctx -> debugTest(ctx.getSource())))));
	}

	private static int giveKit(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		player.getInventory().add(new ItemStack(ModBlocks.BOT_ITEM));
		source.sendSuccess(() -> Component.literal(
			"[MineBot] Đã gửi Máy Đào Bot vào túi bạn (đặt xuống đất để dùng)"), false);
		MineBot.LOGGER.info("[MineBot] kit given to {}", player.getName().getString());
		return 1;
	}

	private static int debugTest(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		String error = BotSelfTest.selfTest(level);
		if (error == null) {
			source.sendSuccess(() -> Component.literal(
				"[MineBot] SelfTest OK (bot placed, dug 1 block, loot ok)"), false);
			MineBot.LOGGER.info("[MineBot] SelfTest PASSED");
			return 1;
		}
		source.sendFailure(Component.literal("[MineBot] SelfTest FAILED: " + error));
		MineBot.LOGGER.error("[MineBot] SelfTest FAILED: {}", error);
		return 0;
	}
}
