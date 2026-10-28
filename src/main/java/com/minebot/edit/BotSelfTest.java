package com.minebot.edit;

import com.minebot.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Headless self-test used by CI: place bot, aim it at a dirt block, mine once,
 * expect dirt in the bot inventory. Returns error text on failure, null on success.
 */
public final class BotSelfTest {
	private BotSelfTest() {}

	public static String selfTest(ServerLevel level) {
		level.getChunk(0, 0);
		BlockPos botPos = new BlockPos(8, 66, 8);
		BlockPos dirtPos = new BlockPos(8, 70, 8);
		level.setBlock(botPos, ModBlocks.BOT_BLOCK.defaultBlockState(), 3, 512);
		if (!(level.getBlockEntity(botPos) instanceof BotBlockEntity bot)) {
			return "bot block entity missing";
		}
		level.setBlock(dirtPos, Blocks.DIRT.defaultBlockState(), 3, 512);
		if (!bot.setCorner(1, dirtPos) || !bot.setCorner(2, dirtPos)) {
			return "setCorner failed";
		}
		if (!bot.start(true)) {
			return "start failed";
		}
		boolean mined = bot.mineOnce(level);
		if (!mined) {
			return "mining step failed";
		}
		int dirt = 0;
		for (ItemStack st : bot.inventoryView()) {
			if (!st.isEmpty() && st.getItem() == net.minecraft.world.item.Items.DIRT) {
				dirt += st.getCount();
			}
		}
		if (dirt != 1) {
			return "expected 1 dirt in bot inventory, got " + dirt;
		}
		if (bot.start(false) && bot.isRunning()) {
			return "stop failed";
		}
		level.removeBlock(dirtPos, false);
		level.removeBlock(botPos, false);
		return null;
	}
}
