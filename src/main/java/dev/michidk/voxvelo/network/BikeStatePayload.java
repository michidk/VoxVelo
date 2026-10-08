package dev.michidk.voxvelo.network;

import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.bike.BikeRiderState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, every tick while riding: what the bike the rider's client simulates is doing. The position
 * travels separately with vanilla vehicle movement; this carries what the server and the other clients cannot see.
 *
 * <p>Wire format (version 1): {@code u8 version, f32 speed, f32 steering, f32 brake, u8 gear, bool pedaling,
 * f32 power, u8 surface, f32 gradient, f32 impact}. A payload with an unknown version is fully consumed and ignored so
 * that a newer client can still talk to an older server (and vice versa) without being disconnected.
 */
public record BikeStatePayload(int version, BikeRiderState state) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	private static final BikeRiderState EMPTY = new BikeRiderState(0.0, 0.0, 0.0, 0, false, 0.0, 0, 0.0, 0.0);

	public static final CustomPacketPayload.Type<BikeStatePayload> TYPE = new CustomPacketPayload.Type<>(VoxVelo.id("bike_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BikeStatePayload> CODEC = CustomPacketPayload.codec(BikeStatePayload::write, BikeStatePayload::read);

	public static BikeStatePayload of(BikeRiderState state) {
		return new BikeStatePayload(PROTOCOL_VERSION, state);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeFloat((float) this.state.speed());
		buf.writeFloat((float) this.state.steering());
		buf.writeFloat((float) this.state.brake());
		buf.writeByte(this.state.gear());
		buf.writeBoolean(this.state.pedaling());
		buf.writeFloat((float) this.state.power());
		buf.writeByte(this.state.surface());
		buf.writeFloat((float) this.state.gradient());
		buf.writeFloat((float) this.state.impact());
	}

	private static BikeStatePayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new BikeStatePayload(version, EMPTY);
		}
		return new BikeStatePayload(version, new BikeRiderState(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUnsignedByte(),
			buf.readBoolean(), buf.readFloat(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat()));
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
