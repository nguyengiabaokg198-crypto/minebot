package com.minebot.networking;

import com.minebot.MineBot;

import net.minecraft.network.codec.ByteBufCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client state of one bot (also used to open the GUI). */
public record BotStatePayload(
		int bx, int by, int bz,
		byte flags,
		byte toolMask,
		int c1x, int c1y, int c1z,
		int c2x, int c2y, int c2z,
		String tools, String inv, String stats) implements CustomPacketPayload {

	public static final byte F_OPEN = 1;
	public static final byte F_VALID = 2;
	public static final byte F_RUNNING = 4;
	public static final byte F_C1 = 8;
	public static final byte F_C2 = 16;

	public static final CustomPacketPayload.Type<BotStatePayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(MineBot.MOD_ID, "state"));

	public static final StreamCodec<ByteBuf, BotStatePayload> CODEC = StreamCodec.of(
		(buf, p) -> {
			buf.writeInt(p.bx());
			buf.writeInt(p.by());
			buf.writeInt(p.bz());
			buf.writeByte(p.flags());
			buf.writeByte(p.toolMask());
			buf.writeInt(p.c1x());
			buf.writeInt(p.c1y());
			buf.writeInt(p.c1z());
			buf.writeInt(p.c2x());
			buf.writeInt(p.c2y());
			buf.writeInt(p.c2z());
			ByteBufCodecs.STRING_UTF8.encode(buf, p.tools());
			ByteBufCodecs.STRING_UTF8.encode(buf, p.inv());
			ByteBufCodecs.STRING_UTF8.encode(buf, p.stats());
		},
		buf -> new BotStatePayload(
			buf.readInt(), buf.readInt(), buf.readInt(), buf.readByte(), buf.readByte(),
			buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
			ByteBufCodecs.STRING_UTF8.decode(buf),
			ByteBufCodecs.STRING_UTF8.decode(buf),
			ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public CustomPacketPayload.Type<BotStatePayload> type() {
		return TYPE;
	}
}
