package com.minebot.registry;

import com.minebot.MineBot;
import com.minebot.edit.BotBlock;
import com.minebot.edit.BotBlockEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlocks {
	private ModBlocks() {}

	public static BlockEntityType<BotBlockEntity> BOT_BE_TYPE;

	public static final Block BOT_BLOCK = registerBot();

	private static Block registerBot() {
		Identifier id = Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "bot");
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = new BotBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
			.strength(3.0f, 6.0f).setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		return block;
	}

	private static Item registerItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(MineBot.MOD_ID, path));
		Item item = new BlockItem(block, new Item.Properties()
			.stacksTo(64).rarity(Rarity.RARE).setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static final Item BOT_ITEM = registerItem("bot", BOT_BLOCK);

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
		Registries.CREATIVE_MODE_TAB,
		Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "main"));

	public static final CreativeModeTab TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
		.title(Component.translatable("itemGroup.minebot.main"))
		.icon(() -> new ItemStack(BOT_ITEM))
		.displayItems((parameters, output) -> {
			output.accept(BOT_ITEM);
		})
		.build();

	@SuppressWarnings("unchecked")
	private static BlockEntityType<BotBlockEntity> createBeType() {
		BlockEntityType<BotBlockEntity>[] holder = new BlockEntityType[1];
		BlockEntityType<BotBlockEntity> type = new BlockEntityType<>(
			(pos, state) -> new BotBlockEntity(holder[0], pos, state),
			Set.of(BOT_BLOCK));
		holder[0] = type;
		return type;
	}

	public static void init() {
		ResourceKey<BlockEntityType<?>> beKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "bot"));
		BOT_BE_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, beKey, createBeType());
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
