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
	private static final int PANEL_H = 214;

	private static final String[] TOOL_NAMES = {"Cuốc", "Rìu", "Xẻng", "Liềm"};

	// snapshot of the last received bot state
	private static int botX, botY, botZ;
	private static boolean valid, running, c1Set, c2Set;
	private static int c1x, c1y, c1z, c2x, c2y, c2z;
	private static String[] toolStr = {"-", "-", "-", "-"};
	private static String invText = "Túi: rỗng";
	private static String statsText = "";

	private int bx, by;

	public BotPanelScreen() {
		super(Component.literal("MineBot"));
	}

	@Override
	protected void init() {
		super.init();
		bx = (this.width - PANEL_W) / 2;
		by = Math.max(8, (this.height - PANEL_H) / 2);

		// Corner row: two aiming buttons
		this.addRenderableWidget(Button.builder(Component.literal("Nhắm Góc 1"),
			b -> send(BotActionPayload.OP_CORNER1, 0)).bounds(bx + 6, by + 58, 116, 18).build());
		this.addRenderableWidget(Button.builder(Component.literal("Nhắm Góc 2"),
			b -> send(BotActionPayload.OP_CORNER2, 0)).bounds(bx + 128, by + 58, 116, 18).build());

		// Tool row: four slots
		for (int i = 0; i < 4; i++) {
			final int slot = i;
			this.addRenderableWidget(Button.builder(Component.literal(TOOL_NAMES[i]),
				b -> send(BotActionPayload.OP_GIVE_TOOL, (byte) slot))
				.bounds(bx + 6 + (i * 60), by + 80, 57, 18).build());
		}

		// Action row
		this.addRenderableWidget(Button.builder(Component.literal("Chạy / Dừng"),
			b -> send(BotActionPayload.OP_RUNNING, (byte) (running ? 0 : 1)))
			.bounds(bx + 6, by + 130, 78, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Nhận hết đồ"),
			b -> send(BotActionPayload.OP_COLLECT_ALL, 0)).bounds(bx + 88, by + 130, 78, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Nhận công cụ"),
			b -> send(BotActionPayload.OP_TAKE_TOOLS, 0)).bounds(bx + 170, by + 130, 78, 20).build());

		// Refresh button
		this.addRenderableWidget(Button.builder(Component.literal("Làm mới"),
			b -> send(BotActionPayload.OP_REQUEST_STATE, 0)).bounds(bx + 6, by + 190, 70, 16).build());

		send(BotActionPayload.OP_REQUEST_STATE, 0);
	}

	private static void send(byte op, byte arg) {
		ClientPlayNetworking.send(new BotActionPayload(botX, botY, botZ, op, arg));
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
		return "(" + x + ", " + y + ", " + z + ")";
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
			invText = "Túi: rỗng";
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
			invText = "Túi: " + used + "/15" + (sb.length() > 0 ? "   " + sb : "");
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(bx, by, bx + PANEL_W, by + PANEL_H, 0xF0122430);
		g.outline(bx, by, PANEL_W, PANEL_H, 0xFF37D06C);
		super.extractRenderState(g, mouseX, mouseY, delta);

		g.centeredText(this.font, "MINEBOT - MÁY ĐÀO", bx + PANEL_W / 2, by + 8, 0xFF37D06C);
		g.text(this.font, "Bot: " + cornerText(botX, botY, botZ)
			+ (valid ? (running ? "   ● CHẠY" : "   ○ dừng") : "   ✖ máy mất"),
			bx + 8, by + 22, 0xFF9FE7FF);
		g.text(this.font,
			"Góc 1: " + (c1Set ? cornerText(c1x, c1y, c1z) : "(nhắm block rồi bấm)")
				+ "   Góc 2: " + (c2Set ? cornerText(c2x, c2y, c2z) : "chưa"),
			bx + 8, by + 36, 0xFFECECEC);
		g.text(this.font, "Ô công cụ (cầm tool rồi bấm để nạp vào):",
			bx + 8, by + 70, 0xFF90A0B5);
		g.text(this.font, "Cuốc: " + toolStr[0] + "   Rìu: " + toolStr[1],
			bx + 8, by + 102, 0xFFECECEC);
		g.text(this.font, "Xẻng: " + toolStr[2] + "   Liềm: " + toolStr[3],
			bx + 8, by + 114, 0xFFECECEC);
		g.text(this.font, invText, bx + 8, by + 157, 0xFF9FE7FF);
		g.text(this.font, statsText, bx + 8, by + 168, 0xFF37D06C);
		g.text(this.font, "Nhắm block → bấm Nhắm Góc 1 / 2 → bấm Chạy",
			bx + 8, by + PANEL_H - 14, 0xFF90A0B5);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
