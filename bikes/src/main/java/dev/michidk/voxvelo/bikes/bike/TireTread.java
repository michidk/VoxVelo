package dev.michidk.voxvelo.bikes.bike;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The tire fitted to a wheel. Each tread is the tire one of the bike types comes with, so its rolling resistance and
 * loose-ground grip are taken from that type's tuning: a stock bike rides exactly as before, and a mixed one blends.
 */
public enum TireTread implements StringRepresentable {
	ROAD("road", BikeType.ROAD),
	GRAVEL("gravel", BikeType.GRAVEL),
	MOUNTAIN("mountain", BikeType.MOUNTAIN);

	public static final Codec<TireTread> CODEC = StringRepresentable.fromEnum(TireTread::values);
	public static final StreamCodec<ByteBuf, TireTread> STREAM_CODEC =
		ByteBufCodecs.idMapper(TireTread::byOrdinal, TireTread::ordinal);

	public final String id;
	/** The bike type that comes with this tire; its tire parameters and its wheel model are used for it. */
	public final BikeType origin;

	TireTread(String id, BikeType origin) {
		this.id = id;
		this.origin = origin;
	}

	public static TireTread nativeFor(BikeType type) {
		return switch (type) {
			case ROAD -> ROAD;
			case GRAVEL -> GRAVEL;
			case MOUNTAIN -> MOUNTAIN;
		};
	}

	public static TireTread byOrdinal(int ordinal) {
		TireTread[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : GRAVEL;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
