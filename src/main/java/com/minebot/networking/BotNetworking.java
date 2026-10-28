package com.minebot.networking;

import com.minebot.MineBot;
import com.minebot.edit.BotBlock;
import com.minebot.edit.BotBlockEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public final class BotNetworking {
	private BotNetworking() {}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(BotActionPayload.TYPE, BotActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BotStatePayload.TYPE, BotStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BotActionPayload.TYPE, (payload, ctx) ->
			ctx.server().executeIfPossible(() -> handle(ctx.player(), payload)));

		// Right click a bot block -> open its GUI
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world.getBlockState(hit.getBlockPos()).getBlock() instanceof BotBlock)) {
				return InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp) {
				sendState(sp, hit.getBlockPos(), true);
			}
			return InteractionResult.SUCCESS;
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
			MineBot.LOGGER.info("[MineBot] lifecycle done"));
	}

	private static void handle(ServerPlayer player, BotActionPayload payload) {
		Level level = player.level();
		BlockPos botPos = new BlockPos(payload.bx(), payload.by(), payload.bz());
		if (level.getBlockEntity(botPos) instanceof BotBlockEntity bot) {
			switch (payload.op()) {
				case BotActionPayload.OP_CORNER1 -> setCorner(player, bot, botPos, 1);
				case BotActionPayload.OP_CORNER2 -> setCorner(player, bot, botPos, 2);
				case BotActionPayload.OP_GIVE_TOOL -> giveTool(player, bot, botPos, payload.arg());
				case BotActionPayload.OP_TAKE_TOOLS -> {
					List<ItemStack> taken = bot.takeAllTools();
					for (ItemStack st : taken) {
						if (!player.getInventory().add(st)) {
							bot.insertExternal(st);
							break;
						}
					}
					player.sendSystemMessage(Component.literal(
						"[MineBot] Đã nhận lại " + taken.size() + " công cụ"));
					sendState(player, botPos, false);
				}
				case BotActionPayload.OP_RUNNING -> {
					boolean want = payload.arg() != 0;
					if (bot.start(want)) {
						player.sendSystemMessage(Component.literal(
							want ? "[MineBot] Bot bắt đầu đào" : "[MineBot] Bot đã dừng"));
					} else {
						player.sendSystemMessage(Component.literal(
							"[MineBot] Không thể bắt đầu: chọn đủ 2 góc (vùng ≤24×24×24)"));
					}
					sendState(player, botPos, false);
				}
				case BotActionPayload.OP_COLLECT_ALL -> {
					List<ItemStack> taken = new ArrayList<>();
					int moved = bot.collectAll(taken);
					int kept = 0;
					for (ItemStack st : taken) {
						if (!player.getInventory().add(st)) {
							bot.insertExternal(st);
							kept++;
						}
					}
					player.sendSystemMessage(Component.literal(
						"[MineBot] Đã chuyển " + moved + " stack"
							+ (kept > 0 ? " (túi đầy: giữ lại " + kept + ")" : "")));
					sendState(player, botPos, false);
				}
				default -> sendState(player, botPos, false);
			}
		} else {
			player.sendSystemMessage(Component.literal("[MineBot] Máy không còn ở chỗ đó"));
		}
	}

	private static void setCorner(ServerPlayer player, BotBlockEntity bot, BlockPos botPos, int which) {
		HitResult hit = player.pick(5.0, 1.0f, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.sendSystemMessage(Component.literal("[MineBot] Nhắm vào một block trước"));
			sendState(player, botPos, false);
			return;
		}
		BlockPos target = ((BlockHitResult) hit).getBlockPos();
		bot.setCorner(which, target);
		player.sendSystemMessage(Component.literal(
			"[MineBot] Góc " + which + ": ("
				+ target.getX() + ", " + target.getY() + ", " + target.getZ() + ")"));
		sendState(player, botPos, false);
	}

	private static void giveTool(ServerPlayer player, BotBlockEntity bot, BlockPos botPos, int slot) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			player.sendSystemMessage(Component.literal("[MineBot] Cầm công cụ trên tay trước"));
			sendState(player, botPos, false);
			return;
		}
		ItemStack given = held.copyWithCount(1);
		if (bot.giveTool(slot, given)) {
			held.shrink(1);
			player.sendSystemMessage(Component.literal("[MineBot] Đã nạp " + toolLabel(slot)));
			sendState(player, botPos, false);
		} else {
			player.sendSystemMessage(Component.literal("[MineBot] Ô công cụ không hợp lệ"));
			sendState(player, botPos, false);
		}
	}

	private static String toolLabel(int slot) {
		return switch (slot) {
			case 0 -> "Cuốc";
			case 1 -> "Rìu";
			case 2 -> "Xẻng";
			case 3 -> "Liềm";
			default -> "Ô";
		};
	}

	public static void sendState(ServerPlayer player, BlockPos botPos, boolean open) {
		Level level = player.level();
		byte flags = 0;
		byte toolMask = 0;
		int c1x = 0, c1y = 0, c1z = 0, c2x = 0, c2y = 0, c2z = 0;
		String tools = "-", inv = "-", stats = "-";
		if (level.getBlockEntity(botPos) instanceof BotBlockEntity bot) {
			flags |= BotStatePayload.F_VALID;
			if (open) {
				flags |= BotStatePayload.F_OPEN;
			}
			if (bot.isRunning()) {
				flags |= BotStatePayload.F_RUNNING;
			}
			if (bot.corner1Set()) {
				flags |= BotStatePayload.F_C1;
				c1x = bot.c1x();
				c1y = bot.c1y();
				c1z = bot.c1z();
			}
			if (bot.corner2Set()) {
				flags |= BotStatePayload.F_C2;
				c2x = bot.c2x();
				c2y = bot.c2y();
				c2z = bot.c2z();
			}
			toolMask = (byte) bot.toolMask();
			tools = bot.toolsDesc();
			inv = bot.invDesc();
			stats = bot.statsDesc();
		} else {
			flags |= BotStatePayload.F_OPEN;
			tools = "-";
			inv = "-";
			stats = "-";
		}
		ServerPlayNetworking.send(player, new BotStatePayload(botPos.getX(), botPos.getY(), botPos.getZ(),
			flags, toolMask, c1x, c1y, c1z, c2x, c2y, c2z, tools, inv, stats));
	}
}
