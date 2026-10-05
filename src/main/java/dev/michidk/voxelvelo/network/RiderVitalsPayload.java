package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.VoxelVelo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the rider's heart rate and what they are willing to share with other players. Sent on join,
 * when something changes, and every few seconds as a heartbeat so stale values expire on the server.
 *
 * <p>Wire format (version 1): u8 version, u8 flags (bit 0 share watts, bit 1 share heart rate), u8 heart rate in
 * bpm (0 = none). Values are clamped on the server; an unknown version is consumed and ignored.
 */
public record RiderVitalsPayload(int version, int flags, int heartRate) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final int FLAG_SHARE_WATTS = 1;
	public static final int FLAG_SHARE_HEART_RATE = 2;

	public static final CustomPacketPayload.Type<RiderVitalsPayload> TYPE = new CustomPacketPayload.Type<>(VoxelVelo.id("rider_vitals"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RiderVitalsPayload> CODEC = CustomPacketPayload.codec(RiderVitalsPayload::write, RiderVitalsPayload::read);

	public static RiderVitalsPayload of(boolean shareWatts, boolean shareHeartRate, int heartRate) {
		int flags = (shareWatts ? FLAG_SHARE_WATTS : 0) | (shareHeartRate ? FLAG_SHARE_HEART_RATE : 0);
		return new RiderVitalsPayload(PROTOCOL_VERSION, flags, heartRate);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeByte(this.flags);
		buf.writeByte(this.heartRate);
	}

	private static RiderVitalsPayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new RiderVitalsPayload(version, 0, 0);
		}
		return new RiderVitalsPayload(version, buf.readUnsignedByte(), buf.readUnsignedByte());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
