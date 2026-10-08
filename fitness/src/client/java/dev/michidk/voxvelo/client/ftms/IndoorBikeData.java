package dev.michidk.voxvelo.client.ftms;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jspecify.annotations.Nullable;

/**
 * One decoded Indoor Bike Data notification (characteristic 0x2AD2). Absent fields are null.
 * Speed is kept only as optional telemetry: Minecraft computes the real bike speed itself.
 *
 * @param speedKmh        instantaneous trainer-reported speed
 * @param cadenceRpm      instantaneous cadence
 * @param powerWatts      instantaneous power
 * @param resistanceLevel unitless trainer resistance level
 * @param heartRate       beats per minute, if the trainer relays one
 */
public record IndoorBikeData(
	@Nullable Double speedKmh,
	@Nullable Double cadenceRpm,
	@Nullable Integer powerWatts,
	@Nullable Integer resistanceLevel,
	@Nullable Integer heartRate
) {
	private static final int MORE_DATA = 1;
	private static final int AVERAGE_SPEED = 1 << 1;
	private static final int INSTANT_CADENCE = 1 << 2;
	private static final int AVERAGE_CADENCE = 1 << 3;
	private static final int TOTAL_DISTANCE = 1 << 4;
	private static final int RESISTANCE_LEVEL = 1 << 5;
	private static final int INSTANT_POWER = 1 << 6;
	private static final int AVERAGE_POWER = 1 << 7;
	private static final int EXPENDED_ENERGY = 1 << 8;
	private static final int HEART_RATE = 1 << 9;
	private static final int METABOLIC_EQUIVALENT = 1 << 10;
	private static final int ELAPSED_TIME = 1 << 11;
	private static final int REMAINING_TIME = 1 << 12;

	/** Returns null if the payload is truncated or otherwise not a valid Indoor Bike Data value. */
	public static @Nullable IndoorBikeData parse(byte[] data) {
		if (data == null || data.length < 2) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		int flags = buf.getShort() & 0xFFFF;
		Double speed = null;
		Double cadence = null;
		Integer power = null;
		Integer resistance = null;
		Integer heartRate = null;

		// Bit 0 is inverted by the spec: the speed field is present when it is NOT set.
		if ((flags & MORE_DATA) == 0) {
			if (buf.remaining() < 2) {
				return null;
			}
			speed = (buf.getShort() & 0xFFFF) * 0.01;
		}
		if ((flags & AVERAGE_SPEED) != 0 && !skip(buf, 2)) {
			return null;
		}
		if ((flags & INSTANT_CADENCE) != 0) {
			if (buf.remaining() < 2) {
				return null;
			}
			cadence = (buf.getShort() & 0xFFFF) * 0.5;
		}
		if ((flags & AVERAGE_CADENCE) != 0 && !skip(buf, 2)) {
			return null;
		}
		if ((flags & TOTAL_DISTANCE) != 0 && !skip(buf, 3)) {
			return null;
		}
		if ((flags & RESISTANCE_LEVEL) != 0) {
			if (buf.remaining() < 2) {
				return null;
			}
			resistance = (int) buf.getShort();
		}
		if ((flags & INSTANT_POWER) != 0) {
			if (buf.remaining() < 2) {
				return null;
			}
			power = (int) buf.getShort();
		}
		if ((flags & AVERAGE_POWER) != 0 && !skip(buf, 2)) {
			return null;
		}
		if ((flags & EXPENDED_ENERGY) != 0 && !skip(buf, 5)) {
			return null;
		}
		if ((flags & HEART_RATE) != 0) {
			if (buf.remaining() < 1) {
				return null;
			}
			heartRate = buf.get() & 0xFF;
		}
		// Remaining optional fields (metabolic equivalent, elapsed and remaining time) are not used.
		return new IndoorBikeData(speed, cadence, power, resistance, heartRate);
	}

	private static boolean skip(ByteBuffer buf, int bytes) {
		if (buf.remaining() < bytes) {
			return false;
		}
		buf.position(buf.position() + bytes);
		return true;
	}
}
