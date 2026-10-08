package dev.michidk.voxvelo.bikes.network;

import dev.michidk.voxvelo.bikes.VoxVelo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the rider's physics preferences. Sent on join and when the player changes them.
 *
 * <p>Wire format (version 1): u8 version, f32 rider mass in kg, f32 speed limit in km/h (0 = none).
 * Values are clamped on the server; an unknown version is consumed and ignored.
 */
public record RiderSettingsPayload(int version, float massKg, float speedLimitKmh) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;

	public static final CustomPacketPayload.Type<RiderSettingsPayload> TYPE = new CustomPacketPayload.Type<>(VoxVelo.id("rider_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RiderSettingsPayload> CODEC = CustomPacketPayload.codec(RiderSettingsPayload::write, RiderSettingsPayload::read);

	public static RiderSettingsPayload of(double massKg, double speedLimitKmh) {
		return new RiderSettingsPayload(PROTOCOL_VERSION, (float) massKg, (float) speedLimitKmh);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeFloat(this.massKg);
		buf.writeFloat(this.speedLimitKmh);
	}

	private static RiderSettingsPayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new RiderSettingsPayload(version, 0.0F, 0.0F);
		}
		return new RiderSettingsPayload(version, buf.readFloat(), buf.readFloat());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
