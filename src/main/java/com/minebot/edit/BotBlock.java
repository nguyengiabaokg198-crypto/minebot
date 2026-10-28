package com.minebot.edit;

import com.minebot.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BotBlock extends Block {
	public BotBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BotBlockEntity(ModBlocks.BOT_BE_TYPE, pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type == ModBlocks.BOT_BE_TYPE && !level.isClientSide()) {
			return (lvl, pos, st, be) -> ((BotBlockEntity) be).serverTick(lvl);
		}
		return null;
	}

	public static class Properties extends net.minecraft.world.level.block.state.BlockBehaviour.Properties {}
}
