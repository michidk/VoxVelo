package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeControlState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the rider's normalized control state. Always carries the full state (not deltas)
 * so a lost or delayed packet is repaired by the next one.
 *
 * <p>Wire format (version 1): {@code u8 version, u16 sequence, f32 power, f32 steering, f32 brake, u8 gear}.
 * A payload with an unknown version is fully consumed and ignored so that a newer client can still
 * talk to an older server (and vice versa) without being disconnected.
 */
public record BikeControlPayload(int version, int sequence, BikeControlState control) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;

	public static final CustomPacketPayload.Type<BikeControlPayload> TYPE = new CustomPacketPayload.Type<>(VoxelVelo.id("bike_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BikeControlPayload> CODEC = CustomPacketPayload.codec(BikeControlPayload::write, BikeControlPayload::read);

	public static BikeControlPayload of(int sequence, BikeControlState control) {
		return new BikeControlPayload(PROTOCOL_VERSION, sequence & 0xFFFF, control);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeShort(this.sequence);
		buf.writeFloat((float) this.control.propulsion());
		buf.writeFloat((float) this.control.steering());
		buf.writeFloat((float) this.control.brake());
		buf.writeByte(this.control.gear());
	}

	private static BikeControlPayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new BikeControlPayload(version, 0, BikeControlState.NEUTRAL);
		}
		int sequence = buf.readUnsignedShort();
		double power = buf.readFloat();
		double steering = buf.readFloat();
		double brake = buf.readFloat();
		int gear = buf.readUnsignedByte();
		return new BikeControlPayload(version, sequence, new BikeControlState(power, steering, brake, gear).sanitized());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
