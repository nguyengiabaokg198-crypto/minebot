package com.minebot.registry;

import com.minebot.MineBot;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The Bot Controller: a wand-like item. Tap-left a block = corner 1,
 * tap-right a block = corner 2, tap-right the air = open the bot GUI.
 * Designed for touch screens (Pojav) - no GUI aiming needed.
 */
public final class ModItems {
	private ModItems() {}

	public static Item CONTROLLER;

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "controller"));
		CONTROLLER = new Item(new Item.Properties()
			.stacksTo(1).rarity(Rarity.UNCOMMON).setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, CONTROLLER);
	}
}
