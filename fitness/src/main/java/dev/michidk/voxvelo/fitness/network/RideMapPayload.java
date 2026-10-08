package dev.michidk.voxvelo.fitness.network;

import dev.michidk.voxvelo.fitness.FitnessMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: draw this recorded ride on a filled map and give it to me.
 *
 * <p>Wire format (version 1): u8 version, utf dimension id, varint distance in metres, varint riding time in
 * seconds, u16 average watts (65535 = none), varint ascent in metres, utf label (the ride's local start time, at
 * most {@value #MAX_LABEL} chars), varint point count (at most {@value #MAX_POINTS}), then per point: i32 block x,
 * i32 block z, bool "a new stretch starts here" (no line from the previous point). The route is downsampled on
 * the client; a ride map needs far fewer points than a 128 pixel map has. An unknown version or an oversized
 * route is consumed and ignored.
 */
public record RideMapPayload(
	int version,
	String dimension,
	int distanceM,
	int timerSeconds,
	int avgPowerWatts,
	int ascentM,
	String label,
	List<Point> points
) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final int MAX_POINTS = 1024;
	public static final int MAX_LABEL = 32;
	public static final int NO_POWER = 65535;

	public static final CustomPacketPayload.Type<RideMapPayload> TYPE = new CustomPacketPayload.Type<>(FitnessMod.id("ride_map"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RideMapPayload> CODEC = CustomPacketPayload.codec(RideMapPayload::write, RideMapPayload::read);

	/** A route point in block coordinates. */
	public record Point(int x, int z, boolean newStretch) {
	}

	public RideMapPayload {
		points = List.copyOf(points);
	}

	public static RideMapPayload of(String dimension, int distanceM, int timerSeconds, int avgPowerWatts, int ascentM, String label, List<Point> points) {
		String trimmed = label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label;
		return new RideMapPayload(PROTOCOL_VERSION, dimension, distanceM, timerSeconds, avgPowerWatts, ascentM, trimmed, points);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION && !this.points.isEmpty();
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeUtf(this.dimension, 256);
		buf.writeVarInt(this.distanceM);
		buf.writeVarInt(this.timerSeconds);
		buf.writeShort(this.avgPowerWatts);
		buf.writeVarInt(this.ascentM);
		buf.writeUtf(this.label, MAX_LABEL);
		buf.writeVarInt(this.points.size());
		for (Point point : this.points) {
			buf.writeInt(point.x());
			buf.writeInt(point.z());
			buf.writeBoolean(point.newStretch());
		}
	}

	private static RideMapPayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new RideMapPayload(version, "", 0, 0, NO_POWER, 0, "", List.of());
		}
		String dimension = buf.readUtf(256);
		int distance = buf.readVarInt();
		int timer = buf.readVarInt();
		int power = buf.readUnsignedShort();
		int ascent = buf.readVarInt();
		String label = buf.readUtf(MAX_LABEL);
		int count = buf.readVarInt();
		if (count < 0 || count > MAX_POINTS) {
			buf.skipBytes(buf.readableBytes());
			return new RideMapPayload(version, dimension, 0, 0, NO_POWER, 0, "", List.of());
		}
		List<Point> points = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			points.add(new Point(buf.readInt(), buf.readInt(), buf.readBoolean()));
		}
		return new RideMapPayload(version, dimension, distance, timer, power, ascent, label, points);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
