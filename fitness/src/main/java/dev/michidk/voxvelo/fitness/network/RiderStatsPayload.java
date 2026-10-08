package dev.michidk.voxvelo.fitness.network;

import dev.michidk.voxvelo.fitness.FitnessMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the ride metrics of the riders the receiver may see. Entries only carry what the
 * rider chose to share; a value the rider keeps private is sent as {@link #NO_WATTS} or {@link #NO_HEART_RATE}.
 *
 * <p>Wire format (version 3): u8 version, varint count, then per entry a UUID, u16 watts, u8 heart rate, float speed in km/h and u16 cadence rpm and u8 gear.
 * At most {@link #MAX_ENTRIES} entries are read; an unknown version is consumed and ignored.
 */
public record RiderStatsPayload(int version, List<Entry> entries) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 3;
	public static final int NO_WATTS = 0xFFFF;
	public static final int NO_HEART_RATE = 0;
	public static final int MAX_ENTRIES = 64;

	public record Entry(UUID player, int watts, int heartRate, float speedKmh, int cadence, int gear) {}

	public static final CustomPacketPayload.Type<RiderStatsPayload> TYPE = new CustomPacketPayload.Type<>(FitnessMod.id("rider_stats"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RiderStatsPayload> CODEC = CustomPacketPayload.codec(RiderStatsPayload::write, RiderStatsPayload::read);

	public static RiderStatsPayload of(List<Entry> entries) {
		return new RiderStatsPayload(PROTOCOL_VERSION, entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
	}

	public boolean isSupported() {
		return this.version == PROTOCOL_VERSION;
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeByte(this.version);
		buf.writeVarInt(this.entries.size());
		for (Entry entry : this.entries) {
			buf.writeUUID(entry.player());
			buf.writeShort(entry.watts());
			buf.writeByte(entry.heartRate());
			buf.writeFloat(entry.speedKmh());
			buf.writeShort(entry.cadence());
			buf.writeByte(entry.gear());
		}
	}

	private static RiderStatsPayload read(RegistryFriendlyByteBuf buf) {
		int version = buf.readUnsignedByte();
		if (version != PROTOCOL_VERSION) {
			buf.skipBytes(buf.readableBytes());
			return new RiderStatsPayload(version, List.of());
		}
		int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readUUID(), buf.readUnsignedShort(), buf.readUnsignedByte(), buf.readFloat(), buf.readUnsignedShort(), buf.readUnsignedByte()));
		}
		return new RiderStatsPayload(version, entries);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
