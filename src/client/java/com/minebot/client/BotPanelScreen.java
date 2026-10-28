package com.minebot.client;

import com.minebot.networking.BotActionPayload;
import com.minebot.networking.BotStatePayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class BotPanelScreen extends Screen {
	private static final int PANEL_W = 250;
	private static final int PANEL_H = 226;

	private static final String[] TOOL_NAMES = {"Cuốc", "Rìu", "Xẻng", "Liềm"};

	// snapshot of the last received bot state
	private static int botX, botY, botZ;
	private static boolean valid, running, c1Set, c2Set;
	private static int c1x, c1y, c1z, c2x, c2y, c2z;
	private static String[] toolStr = {"-", "-", "-", "-"};
	private static String invText = "Túi máy: rỗng";
	private static String statsText = "";

	private int bx, by;

	public BotPanelScreen() {
		super(Component.literal("MineBot"));
	}

	@Override
	protected void init() {
		super.init();
		bx = (this.width - PANEL_W) / 2;
		by = Math.max(6, (this.height - PANEL_H) / 2);

		// ---- QUICK region presets (one tap, no aiming) ----
		this.addRenderableWidget(Button.builder(Component.literal("Quanh máy 9×9×9"),
			b -> send(BotActionPayload.OP_QUICK, 0)).bounds(bx + 6, by + 50, 116, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("Giếng sâu 5×5×16"),
			b -> send(BotActionPayload.OP_QUICK, 1)).bounds(bx + 128, by + 50, 116, 18).build());

		// ---- manual corners (aim BEFORE opening this GUI) ----
		this.addRenderableWidget(Button.builder(Component.literal("Chốt Góc 1"),
			b -> send(BotActionPayload.OP_CORNER1, 0)).bounds(bx + 6, by + 92, 116, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("Chốt Góc 2"),
			b -> send(BotActionPayload.OP_CORNER2, 0)).bounds(bx + 128, by + 92, 116, 18).build());

		// ---- tool slots ----
		for (int i = 0; i < 4; i++) {
			final int slot = i;
			this.addRenderableWidget(Button.builder(Component.literal(TOOL_NAMES[i]),
				b -> send(BotActionPayload.OP_GIVE_TOOL, (byte) slot))
				.bounds(bx + 6 + (i * 60), by + 134, 57, 18).build());
		}

		// ---- actions ----
		this.addRenderableWidget(Button.builder(Component.literal("CHẠY / DỪNG"),
			b -> send(BotActionPayload.OP_RUNNING, (byte) (running ? 0 : 1)))
			.bounds(bx + 6, by + 182, 78, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Nhận hết đồ"),
			b -> send(BotActionPayload.OP_COLLECT_ALL, 0)).bounds(bx + 88, by + 182, 78, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Nhận công cụ"),
			b -> send(BotActionPayload.OP_TAKE_TOOLS, 0)).bounds(bx + 170, by + 182, 78, 20).build());

		send(BotActionPayload.OP_REQUEST_STATE, 0);
	}

	private static void send(int op, int arg) {
		ClientPlayNetworking.send(new BotActionPayload(botX, botY, botZ, (byte) op, (byte) arg));
	}

	private static String shortName(String id) {
		int idx = id.indexOf(':');
		String name = idx >= 0 ? id.substring(idx + 1) : id;
		String[] parts = name.split("_");
		StringBuilder sb = new StringBuilder();
		for (String s : parts) {
			if (!s.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
			}
		}
		return sb.toString();
	}

	private static String cornerText(int x, int y, int z) {
		return "(" + x + "," + y + "," + z + ")";
	}

	public static void handleState(BotStatePayload p) {
		botX = p.bx();
		botY = p.by();
		botZ = p.bz();
		valid = (p.flags() & BotStatePayload.F_VALID) != 0;
		running = (p.flags() & BotStatePayload.F_RUNNING) != 0;
		c1Set = (p.flags() & BotStatePayload.F_C1) != 0;
		c2Set = (p.flags() & BotStatePayload.F_C2) != 0;
		boolean open = (p.flags() & BotStatePayload.F_OPEN) != 0;
		if (valid) {
			c1x = p.c1x();
			c1y = p.c1y();
			c1z = p.c1z();
			c2x = p.c2x();
			c2y = p.c2y();
			c2z = p.c2z();
		} else {
			c1x = c1y = c1z = c2x = c2y = c2z = 0;
		}
		// tools
		String[] toolParts = p.tools().split(";", -1);
		for (int i = 0; i < 4; i++) {
			toolStr[i] = "-";
			if (toolParts.length > i && !toolParts[i].isBlank()) {
				String v = toolParts[i];
				if (v.equals("-")) {
					toolStr[i] = "rỗng";
				} else {
					String[] f = v.split("\\|");
					if (f.length >= 3) {
						toolStr[i] = shortName(f[0]) + " " + f[1] + "/" + f[2];
					}
				}
			}
		}
		// inventory
		if (!valid) {
			invText = "Túi máy: rỗng";
		} else {
			int used = 0;
			StringBuilder sb = new StringBuilder();
			for (String part : p.inv().split(";")) {
				if (part.isEmpty() || part.equals("-")) {
					continue;
				}
				String[] f = part.split("\\|");
				if (f.length >= 2 && !f[1].equals("0")) {
					used++;
					if (sb.length() < 60) {
						if (sb.length() > 0) {
							sb.append(", ");
						}
						sb.append(shortName(f[0])).append('×').append(f[1]);
					}
				}
			}
			invText = "Túi máy: " + used + "/15" + (sb.length() > 0 ? "  " + sb : "");
		}
		// stats
		StringBuilder sb2 = new StringBuilder();
		for (String part : p.stats().split(";")) {
			if (part.isEmpty() || part.equals("-")) {
				continue;
			}
			String[] f = part.split("\\|");
			if (f.length >= 2 && sb2.length() < 52) {
				if (sb2.length() > 0) {
					sb2.append(", ");
				}
				sb2.append(shortName(f[0])).append('×').append(f[1]);
			}
		}
		statsText = sb2.length() > 0 ? "Đã đào: " + sb2 : "";
		if (open) {
			Minecraft.getInstance().setScreenAndShow(new BotPanelScreen());
		}
	}

	private String boxText() {
		if (!c1Set || !c2Set) {
			return "Hộp đào: CHƯA ĐỦ 2 GÓC — bấm 1 nút Vùng nhanh ở trên!";
		}
		int w = Math.abs(c1x - c2x) + 1;
		int h = Math.abs(c1y - c2y) + 1;
		int d = Math.abs(c1z - c2z) + 1;
		long n = (long) w * h * d;
		return "Hộp đào: " + w + "×" + h + "×" + d + " (" + n + " block)";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(bx, by, bx + PANEL_W, by + PANEL_H, 0xF0122430);
		g.outline(bx, by, PANEL_W, PANEL_H, 0xFF37D06C);
		super.extractRenderState(g, mouseX, mouseY, delta);

		g.centeredText(this.font, "MINEBOT - MÁY ĐÀO", bx + PANEL_W / 2, by + 7, 0xFF37D06C);
		g.text(this.font, "Bot: " + cornerText(botX, botY, botZ)
			+ (valid ? (running ? "  ● ĐANG CHẠY" : "  ○ đang dừng") : "  ✖ máy mất"),
			bx + 8, by + 19, 0xFF9FE7FF);
		g.text(this.font, boxText(), bx + 8, by + 31, 0xFFECECEC);

		g.text(this.font, "VÙNG NHANH (bấm 1 nút là xong):", bx + 8, by + 43, 0xFF90A0B5);

		g.text(this.font, "2 GÓC TỰ CHỌN (cầm BỘ ĐIỀU KHIỂN: tap trái = Góc 1, tap phải = Góc 2):",
			bx + 8, by + 85, 0xFF90A0B5);

		g.text(this.font, "CÔNG CỤ (cầm tool rồi bấm nút để nạp cho bot):",
			bx + 8, by + 127, 0xFF90A0B5);
		g.text(this.font, "Cuốc: " + toolStr[0] + "  Rìu: " + toolStr[1]
			+ "  Xẻng: " + toolStr[2] + "  Liềm: " + toolStr[3],
			bx + 8, by + 156, 0xFFECECEC);

		g.text(this.font, invText, bx + 8, by + 208 - 12, 0xFF9FE7FF);
		g.text(this.font, statsText, bx + 8, by + 208 - 2, 0xFF37D06C);
		g.text(this.font, "tap phải MÁY hoặc tap không khi cầm Điều Khiển = mở bảng này",
			bx + 8, by + PANEL_H - 8, 0xFF7E8CA0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
