package com.minebot.networking;

import com.minebot.MineBot;
import com.minebot.edit.BotBlock;
import com.minebot.edit.BotBlockEntity;
import com.minebot.registry.ModItems;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BotNetworking {
	private BotNetworking() {}

	/** Last bot each player interacted with (server thread only). */
	private static final Map<UUID, BlockPos> ACTIVE = new HashMap<>();

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(BotActionPayload.TYPE, BotActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BotStatePayload.TYPE, BotStatePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BotActionPayload.TYPE, (payload, ctx) ->
			ctx.server().executeIfPossible(() -> handle(ctx.player(), payload)));

		// ---- tap PHAI vao block ----
		// - bot block      -> mo GUI (bat ky thu gi tren tay)
		// - block khac     -> neu dang cam Dieu Khien: chot Goc 2
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.getBlockState(hit.getBlockPos()).getBlock() instanceof BotBlock) {
				if (world.isClientSide()) {
					return InteractionResult.SUCCESS;
				}
				if (player instanceof ServerPlayer sp) {
					setActive(sp, hit.getBlockPos());
					sendState(sp, hit.getBlockPos(), true);
				}
				return InteractionResult.SUCCESS;
			}
			if (player.getMainHandItem().getItem() == ModItems.CONTROLLER) {
				if (world.isClientSide()) {
					return InteractionResult.SUCCESS; // consume, khong thuc hien hanh vi khac
				}
				if (player instanceof ServerPlayer sp) {
					controllerCorner(sp, 2, hit.getBlockPos());
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// ---- tap TRAI (dap) block khi cam Dieu Khien -> chot Goc 1, khong pha block ----
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (player.getMainHandItem().getItem() != ModItems.CONTROLLER) {
				return InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				return InteractionResult.FAIL; // chan viec pha block
			}
			if (player instanceof ServerPlayer sp) {
				controllerCorner(sp, 1, pos);
			}
			return InteractionResult.FAIL;
		});

		// ---- tap PHAI vao khoang khong khi cam Dieu Khien -> mo GUI may gan nhat ----
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player.getItemInHand(hand).getItem() != ModItems.CONTROLLER) {
				return InteractionResult.PASS;
			}
			if (world.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp) {
				BlockPos bot = activeBot(sp);
				if (bot == null) {
					sp.sendSystemMessage(Component.literal(
						"[MineBot] Không thấy máy nào gần bạn (trong 6 block) — hãy đặt Máy Đào Bot xuống trước"));
					return InteractionResult.FAIL;
				}
				sendState(sp, bot, true);
			}
			return InteractionResult.SUCCESS;
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE.clear();
			MineBot.LOGGER.info("[MineBot] lifecycle done");
		});
	}

	// ---------- controller helpers ----------

	private static void setActive(ServerPlayer player, BlockPos pos) {
		ACTIVE.put(player.getUUID(), pos);
	}

	private static void controllerCorner(ServerPlayer player, int which, BlockPos target) {
		BlockPos botPos = activeBot(player);
		if (botPos == null) {
			player.sendSystemMessage(Component.literal(
				"[MineBot] Chưa có máy nào gần bạn — đặt Máy Đào Bot xuống rồi click phải vào máy"));
			return;
		}
		if (player.level().getBlockEntity(botPos) instanceof BotBlockEntity bot) {
			bot.setCorner(which, target);
			player.sendSystemMessage(Component.literal(
				"[MineBot] Đã chốt Góc " + which + ": ("
					+ target.getX() + ", " + target.getY() + ", " + target.getZ() + ")"));
		}
	}

	/** Active bot of the player, or nearest bot within 6 blocks. Null when none. */
	private static BlockPos activeBot(ServerPlayer player) {
		BlockPos saved = ACTIVE.get(player.getUUID());
		if (saved != null) {
			if (player.level().getBlockEntity(saved) instanceof BotBlockEntity) {
				return saved;
			}
			ACTIVE.remove(player.getUUID());
		}
		BlockPos base = player.blockPosition();
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					BlockPos p = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					if (player.level().getBlockEntity(p) instanceof BotBlockEntity) {
						ACTIVE.put(player.getUUID(), p);
						return p;
					}
				}
			}
		}
		return null;
	}

	// ---------- payload handling ----------

	private static void handle(ServerPlayer player, BotActionPayload payload) {
		Level level = player.level();
		BlockPos botPos = new BlockPos(payload.bx(), payload.by(), payload.bz());
		if (level.getBlockEntity(botPos) instanceof BotBlockEntity bot) {
			ACTIVE.put(player.getUUID(), botPos);
			switch (payload.op()) {
				case BotActionPayload.OP_CORNER1 -> guiCorner(player, bot, botPos, 1);
				case BotActionPayload.OP_CORNER2 -> guiCorner(player, bot, botPos, 2);
				case BotActionPayload.OP_QUICK -> quickRegion(player, bot, botPos, payload.arg());
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
							want ? "[MineBot] Bot bắt đầu đào!" : "[MineBot] Bot đã dừng"));
					} else {
						player.sendSystemMessage(Component.literal(
							"[MineBot] Chưa chạy được: hãy bấm 1 nút Vùng nhanh, hoặc chốt đủ 2 góc"));
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
						"[MineBot] Đã chuyển " + moved + " chồng đồ"
							+ (kept > 0 ? " (túi đầy: giữ lại " + kept + ")" : "")));
					sendState(player, botPos, false);
				}
				default -> sendState(player, botPos, false);
			}
		} else {
			player.sendSystemMessage(Component.literal("[MineBot] Máy không còn ở chỗ đó"));
		}
	}

	/** GUI corner buttons: use the look direction the player had BEFORE opening the GUI. */
	private static void guiCorner(ServerPlayer player, BotBlockEntity bot, BlockPos botPos, int which) {
		HitResult hit = player.pick(5.0, 1.0f, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.sendSystemMessage(Component.literal(
				"[MineBot] Bạn không đang nhắm block nào — nhìn block rồi mở GUI lại"));
			sendState(player, botPos, false);
			return;
		}
		BlockPos target = ((BlockHitResult) hit).getBlockPos();
		bot.setCorner(which, target);
		player.sendSystemMessage(Component.literal(
			"[MineBot] Đã chốt Góc " + which + ": ("
				+ target.getX() + ", " + target.getY() + ", " + target.getZ() + ")"));
		sendState(player, botPos, false);
	}

	/** One-tap region presets relative to the machine. */
	private static void quickRegion(ServerPlayer player, BotBlockEntity bot, BlockPos botPos, int mode) {
		BlockPos bp = bot.getBlockPos();
		BlockPos p1;
		BlockPos p2;
		if (mode == 0) {
			// khoi 9x9x9 quanh may
			p1 = new BlockPos(bp.getX() - 4, bp.getY() - 4, bp.getZ() - 4);
			p2 = new BlockPos(bp.getX() + 4, bp.getY() + 4, bp.getZ() + 4);
			player.sendSystemMessage(Component.literal("[MineBot] Vùng nhanh: khối 9×9×9 quanh máy"));
		} else {
			// cot 5x5 sau 16 lop ben duoi may
			p1 = new BlockPos(bp.getX() - 2, bp.getY() - 16, bp.getZ() - 2);
			p2 = new BlockPos(bp.getX() + 2, bp.getY() - 1, bp.getZ() + 2);
			player.sendSystemMessage(Component.literal("[MineBot] Vùng nhanh: giếng 5×5 sâu 16 dưới máy"));
		}
		bot.setCorner(1, p1);
		bot.setCorner(2, p2);
		sendState(player, botPos, false);
	}

	private static void giveTool(ServerPlayer player, BotBlockEntity bot, BlockPos botPos, int slot) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			player.sendSystemMessage(Component.literal("[MineBot] Hãy cầm công cụ trên tay trước"));
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
		}
		ServerPlayNetworking.send(player, new BotStatePayload(botPos.getX(), botPos.getY(), botPos.getZ(),
			flags, toolMask, c1x, c1y, c1z, c2x, c2y, c2z, tools, inv, stats));
	}
}
