package com.minebot.networking;

import com.minebot.MineBot;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server request sent to one bot. */
public record BotActionPayload(int bx, int by, int bz, byte op, byte arg) implements CustomPacketPayload {
	public static final byte OP_REQUEST_STATE = 0;
	public static final byte OP_CORNER1 = 1;
	public static final byte OP_CORNER2 = 2;
	public static final byte OP_GIVE_TOOL = 3;
	public static final byte OP_TAKE_TOOLS = 4;
	public static final byte OP_RUNNING = 5;
	public static final byte OP_COLLECT_ALL = 6;

	public static final CustomPacketPayload.Type<BotActionPayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "action"));

	public static final StreamCodec<ByteBuf, BotActionPayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeInt(p.bx());
			buf.writeInt(p.by());
			buf.writeInt(p.bz());
			buf.writeByte(p.op());
			buf.writeByte(p.arg());
		},
		buf -> new BotActionPayload(
			buf.readInt(), buf.readInt(), buf.readInt(), buf.readByte(), buf.readByte()));

	@Override
	public CustomPacketPayload.Type<BotActionPayload> type() {
		return TYPE;
	}
}
