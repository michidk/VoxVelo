package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.VoxelVelo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the watts and heart rate of the riders the receiver may see. Entries only carry what the
 * rider chose to share; a value the rider keeps private is sent as {@link #NO_WATTS} or {@link #NO_HEART_RATE}.
 *
 * <p>Wire format (version 1): u8 version, varint count, then per entry a UUID, u16 watts and u8 heart rate.
 * At most {@link #MAX_ENTRIES} entries are read; an unknown version is consumed and ignored.
 */
public record RiderStatsPayload(int version, List<Entry> entries) implements CustomPacketPayload {
	public static final int PROTOCOL_VERSION = 1;
	public static final int NO_WATTS = 0xFFFF;
	public static final int NO_HEART_RATE = 0;
	public static final int MAX_ENTRIES = 64;

	public record Entry(UUID player, int watts, int heartRate) {}

	public static final CustomPacketPayload.Type<RiderStatsPayload> TYPE = new CustomPacketPayload.Type<>(VoxelVelo.id("rider_stats"));
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
			entries.add(new Entry(buf.readUUID(), buf.readUnsignedShort(), buf.readUnsignedByte()));
		}
		return new RiderStatsPayload(version, entries);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
