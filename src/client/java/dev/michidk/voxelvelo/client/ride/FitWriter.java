package dev.michidk.voxelvelo.client.ride;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes a ride as a Garmin FIT activity file (protocol 1.0, profile 21.x), the format Strava, Garmin Connect,
 * intervals.icu, TrainingPeaks and friends import.
 *
 * <p>The file holds, in this order: {@code file_id}, a timer start {@code event}, one {@code record} per sample
 * (with timer stop/start events around each interruption), a final timer stop, one {@code lap}, one
 * {@code session} and the {@code activity}. The sport is cycling with the sub-sport "virtual activity", which
 * makes Strava file it as a virtual ride.
 *
 * <p>Minecraft has no latitude and longitude. With {@link Options#includePosition} the ride is laid onto the globe
 * at {@link Options#originLat}/{@link Options#originLon}: the first sample sits at the origin, one block is one metre,
 * east is +X and south is +Z. Samples in another dimension than the first one get no position. Without it the file
 * has no GPS data and only the distance, like an indoor ride.
 */
public final class FitWriter {
	/** Seconds from the Unix epoch to the FIT epoch, 1989-12-31T00:00:00Z. */
	public static final long FIT_EPOCH_OFFSET_SECONDS = 631_065_600L;
	private static final double METRES_PER_DEGREE = 111_320.0;

	private static final int PROFILE_VERSION = 2132;
	private static final int MANUFACTURER_DEVELOPMENT = 255;
	private static final int PRODUCT_NAME_SIZE = 16;

	// Global message numbers.
	private static final int MESG_FILE_ID = 0;
	private static final int MESG_SESSION = 18;
	private static final int MESG_LAP = 19;
	private static final int MESG_RECORD = 20;
	private static final int MESG_EVENT = 21;
	private static final int MESG_ACTIVITY = 34;

	// Base types.
	private static final int ENUM = 0x00;
	private static final int UINT8 = 0x02;
	private static final int STRING = 0x07;
	private static final int SINT16 = 0x83;
	private static final int UINT16 = 0x84;
	private static final int SINT32 = 0x85;
	private static final int UINT32 = 0x86;
	private static final int UINT32Z = 0x8C;

	private static final int INVALID_UINT8 = 0xFF;
	private static final int INVALID_UINT16 = 0xFFFF;
	private static final int INVALID_SINT16 = 0x7FFF;
	private static final int INVALID_SINT32 = 0x7FFFFFFF;
	private static final long INVALID_UINT32 = 0xFFFFFFFFL;

	private static final int SPORT_CYCLING = 2;
	private static final int SUB_SPORT_VIRTUAL_ACTIVITY = 58;
	private static final int EVENT_TIMER = 0;
	private static final int EVENT_LAP = 9;
	private static final int EVENT_SESSION = 8;
	private static final int EVENT_ACTIVITY = 26;
	private static final int EVENT_TYPE_START = 0;
	private static final int EVENT_TYPE_STOP = 1;
	private static final int EVENT_TYPE_STOP_ALL = 4;

	// Local message types: each message kind keeps its own definition for the whole file.
	private static final int LOCAL_FILE_ID = 0;
	private static final int LOCAL_EVENT = 1;
	private static final int LOCAL_RECORD = 2;
	private static final int LOCAL_LAP = 3;
	private static final int LOCAL_SESSION = 4;
	private static final int LOCAL_ACTIVITY = 5;

	private static final int[] CRC_TABLE = {
		0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
		0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400
	};

	/**
	 * @param includePosition       write latitude and longitude
	 * @param originLat             latitude of the first sample, in degrees
	 * @param originLon             longitude of the first sample, in degrees
	 * @param utcOffsetSeconds      the local time zone, for the activity's local timestamp
	 */
	public record Options(boolean includePosition, double originLat, double originLon, int utcOffsetSeconds) {
	}

	private record Field(int number, int size, int baseType) {
	}

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	private FitWriter() {
	}

	public static byte[] write(RideRecording ride, Options options) {
		return new FitWriter().encode(ride, options);
	}

	/** FIT timestamp (seconds since the FIT epoch) of a Unix time in milliseconds. */
	public static long fitTime(long unixMillis) {
		return Math.floorDiv(unixMillis, 1000L) - FIT_EPOCH_OFFSET_SECONDS;
	}

	/** Degrees to FIT semicircles (2^31 semicircles are 180 degrees). */
	public static int semicircles(double degrees) {
		return (int) Math.round(degrees * (2147483648.0 / 180.0));
	}

	/** Latitude and longitude in degrees of a block position relative to the first sample, see the class comment. */
	public static double[] toLatLon(double dx, double dz, Options options) {
		double lat = options.originLat() - dz / METRES_PER_DEGREE;
		double lon = options.originLon() + dx / (METRES_PER_DEGREE * Math.cos(Math.toRadians(options.originLat())));
		lat = Math.max(-89.0, Math.min(89.0, lat));
		lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
		return new double[] {lat, lon};
	}

	private byte[] encode(RideRecording ride, Options options) {
		List<RideSample> samples = ride.samples();
		RideSummary summary = ride.summary();
		long start = fitTime(samples.isEmpty() ? ride.startMillis() : samples.getFirst().timeMillis());
		long end = samples.isEmpty() ? fitTime(ride.endMillis()) : fitTime(samples.getLast().timeMillis());
		end = Math.max(start, end);
		RideSample origin = samples.isEmpty() ? null : samples.getFirst();
		boolean position = options.includePosition() && origin != null;

		this.define(LOCAL_FILE_ID, MESG_FILE_ID,
			new Field(0, 1, ENUM), new Field(1, 2, UINT16), new Field(2, 2, UINT16),
			new Field(3, 4, UINT32Z), new Field(4, 4, UINT32), new Field(8, PRODUCT_NAME_SIZE, STRING));
		this.header(LOCAL_FILE_ID);
		this.u8(4); // activity file
		this.u16(MANUFACTURER_DEVELOPMENT);
		this.u16(1);
		this.u32((ride.startMillis() & 0x7FFFFFFFL) | 1L);
		this.u32(start);
		this.string("VoxelVelo", PRODUCT_NAME_SIZE);

		this.define(LOCAL_EVENT, MESG_EVENT, new Field(253, 4, UINT32), new Field(0, 1, ENUM), new Field(1, 1, ENUM));
		this.event(start, EVENT_TIMER, EVENT_TYPE_START);

		this.define(LOCAL_RECORD, MESG_RECORD, recordFields(position));
		RideSample previous = null;
		for (RideSample sample : samples) {
			long time = fitTime(sample.timeMillis());
			if (previous != null && !RideSummary.continues(previous, sample)) {
				this.event(fitTime(previous.timeMillis()), EVENT_TIMER, EVENT_TYPE_STOP_ALL);
				this.event(time, EVENT_TIMER, EVENT_TYPE_START);
			}
			this.record(time, sample, origin, position, options);
			previous = sample;
		}
		this.event(end, EVENT_TIMER, EVENT_TYPE_STOP_ALL);

		int startLat = INVALID_SINT32;
		int startLon = INVALID_SINT32;
		int endLat = INVALID_SINT32;
		int endLon = INVALID_SINT32;
		if (position) {
			double[] first = toLatLon(0, 0, options);
			startLat = semicircles(first[0]);
			startLon = semicircles(first[1]);
			RideSample last = samples.getLast();
			if (last.dimension().equals(origin.dimension())) {
				double[] ll = toLatLon(last.x() - origin.x(), last.z() - origin.z(), options);
				endLat = semicircles(ll[0]);
				endLon = semicircles(ll[1]);
			}
		}

		this.define(LOCAL_LAP, MESG_LAP,
			new Field(253, 4, UINT32), new Field(0, 1, ENUM), new Field(1, 1, ENUM), new Field(2, 4, UINT32),
			new Field(3, 4, SINT32), new Field(4, 4, SINT32), new Field(5, 4, SINT32), new Field(6, 4, SINT32),
			new Field(7, 4, UINT32), new Field(8, 4, UINT32), new Field(9, 4, UINT32), new Field(11, 2, UINT16),
			new Field(13, 2, UINT16), new Field(14, 2, UINT16), new Field(15, 1, UINT8), new Field(16, 1, UINT8),
			new Field(17, 1, UINT8), new Field(18, 1, UINT8), new Field(19, 2, UINT16), new Field(20, 2, UINT16),
			new Field(21, 2, UINT16), new Field(22, 2, UINT16), new Field(25, 1, ENUM), new Field(39, 1, ENUM),
			new Field(254, 2, UINT16));
		this.header(LOCAL_LAP);
		this.u32(end);
		this.u8(EVENT_LAP);
		this.u8(EVENT_TYPE_STOP);
		this.u32(start);
		this.s32(startLat);
		this.s32(startLon);
		this.s32(endLat);
		this.s32(endLon);
		this.totals(summary);
		this.u8(SPORT_CYCLING);
		this.u8(SUB_SPORT_VIRTUAL_ACTIVITY);
		this.u16(0);

		this.define(LOCAL_SESSION, MESG_SESSION,
			new Field(253, 4, UINT32), new Field(0, 1, ENUM), new Field(1, 1, ENUM), new Field(2, 4, UINT32),
			new Field(3, 4, SINT32), new Field(4, 4, SINT32), new Field(5, 1, ENUM), new Field(6, 1, ENUM),
			new Field(7, 4, UINT32), new Field(8, 4, UINT32), new Field(9, 4, UINT32), new Field(11, 2, UINT16),
			new Field(14, 2, UINT16), new Field(15, 2, UINT16), new Field(16, 1, UINT8), new Field(17, 1, UINT8),
			new Field(18, 1, UINT8), new Field(19, 1, UINT8), new Field(20, 2, UINT16), new Field(21, 2, UINT16),
			new Field(22, 2, UINT16), new Field(23, 2, UINT16), new Field(25, 2, UINT16), new Field(26, 2, UINT16),
			new Field(34, 2, UINT16), new Field(254, 2, UINT16));
		this.header(LOCAL_SESSION);
		this.u32(end);
		this.u8(EVENT_SESSION);
		this.u8(EVENT_TYPE_STOP);
		this.u32(start);
		this.s32(startLat);
		this.s32(startLon);
		this.u8(SPORT_CYCLING);
		this.u8(SUB_SPORT_VIRTUAL_ACTIVITY);
		this.totals(summary);
		this.u16(0); // first lap index
		this.u16(1); // number of laps
		this.u16(summary.normalizedPower() >= 0 ? summary.normalizedPower() : INVALID_UINT16);
		this.u16(0);

		this.define(LOCAL_ACTIVITY, MESG_ACTIVITY,
			new Field(253, 4, UINT32), new Field(0, 4, UINT32), new Field(1, 2, UINT16), new Field(2, 1, ENUM),
			new Field(3, 1, ENUM), new Field(4, 1, ENUM), new Field(5, 4, UINT32));
		this.header(LOCAL_ACTIVITY);
		this.u32(end);
		this.u32(scaled(summary.timerSeconds(), 1000));
		this.u16(1);
		this.u8(0); // manual
		this.u8(EVENT_ACTIVITY);
		this.u8(EVENT_TYPE_STOP);
		this.u32(end + options.utcOffsetSeconds());

		return this.finish();
	}

	private static Field[] recordFields(boolean position) {
		Field timestamp = new Field(253, 4, UINT32);
		Field altitude = new Field(2, 2, UINT16);
		Field heartRate = new Field(3, 1, UINT8);
		Field cadence = new Field(4, 1, UINT8);
		Field distance = new Field(5, 4, UINT32);
		Field speed = new Field(6, 2, UINT16);
		Field power = new Field(7, 2, UINT16);
		Field grade = new Field(9, 2, SINT16);
		if (position) {
			return new Field[] {timestamp, new Field(0, 4, SINT32), new Field(1, 4, SINT32), altitude, heartRate, cadence,
				distance, speed, power, grade};
		}
		return new Field[] {timestamp, altitude, heartRate, cadence, distance, speed, power, grade};
	}

	private void record(long time, RideSample sample, RideSample origin, boolean position, Options options) {
		this.header(LOCAL_RECORD);
		this.u32(time);
		if (position) {
			if (sample.dimension().equals(origin.dimension())) {
				double[] ll = toLatLon(sample.x() - origin.x(), sample.z() - origin.z(), options);
				this.s32(semicircles(ll[0]));
				this.s32(semicircles(ll[1]));
			} else {
				this.s32(INVALID_SINT32);
				this.s32(INVALID_SINT32);
			}
		}
		// Altitude: scale 5, offset 500 m. One block is one metre, so y is the height above the void.
		this.u16(clamp(Math.round((sample.y() + 500.0) * 5.0), 0, INVALID_UINT16 - 1));
		this.u8(sample.hasHeartRate() ? clamp(sample.heartRate(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u8(sample.hasCadence() ? clamp(sample.cadenceRpm(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u32(scaled(sample.distanceM(), 100));
		this.u16(clamp(Math.round(sample.speedMs() * 1000.0), 0, INVALID_UINT16 - 1));
		this.u16(clamp(sample.powerWatts(), 0, INVALID_UINT16 - 1));
		this.s16(clamp(Math.round(sample.gradient() * 100.0 * 100.0), -INVALID_SINT16 + 1, INVALID_SINT16 - 1));
	}

	/** The totals shared by lap and session, from total_elapsed_time to total_descent (lap and session order agree). */
	private void totals(RideSummary summary) {
		this.u32(scaled(summary.elapsedSeconds(), 1000));
		this.u32(scaled(summary.timerSeconds(), 1000));
		this.u32(scaled(summary.distanceM(), 100));
		this.u16(clamp(summary.calories(), 0, INVALID_UINT16 - 1));
		double avgSpeed = summary.timerSeconds() > 0 ? summary.distanceM() / summary.timerSeconds() : 0;
		this.u16(clamp(Math.round(avgSpeed * 1000.0), 0, INVALID_UINT16 - 1));
		this.u16(clamp(Math.round(summary.maxSpeedMs() * 1000.0), 0, INVALID_UINT16 - 1));
		this.u8(summary.avgHeartRate() > 0 ? clamp(summary.avgHeartRate(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u8(summary.maxHeartRate() > 0 ? clamp(summary.maxHeartRate(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u8(summary.avgCadenceRpm() >= 0 ? clamp(summary.avgCadenceRpm(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u8(summary.maxCadenceRpm() >= 0 ? clamp(summary.maxCadenceRpm(), 0, INVALID_UINT8 - 1) : INVALID_UINT8);
		this.u16(summary.avgPowerWatts() >= 0 ? clamp(summary.avgPowerWatts(), 0, INVALID_UINT16 - 1) : INVALID_UINT16);
		this.u16(summary.avgPowerWatts() >= 0 ? clamp(summary.maxPowerWatts(), 0, INVALID_UINT16 - 1) : INVALID_UINT16);
		this.u16(clamp(Math.round(summary.ascentM()), 0, INVALID_UINT16 - 1));
		this.u16(clamp(Math.round(summary.descentM()), 0, INVALID_UINT16 - 1));
	}

	private void event(long time, int event, int type) {
		this.header(LOCAL_EVENT);
		this.u32(time);
		this.u8(event);
		this.u8(type);
	}

	private void define(int local, int global, Field... fields) {
		this.out.write(0x40 | local);
		this.out.write(0); // reserved
		this.out.write(0); // little endian
		this.u16(global);
		this.out.write(fields.length);
		for (Field field : fields) {
			this.out.write(field.number());
			this.out.write(field.size());
			this.out.write(field.baseType());
		}
	}

	private void header(int local) {
		this.out.write(local);
	}

	private byte[] finish() {
		byte[] data = this.out.toByteArray();
		byte[] file = new byte[14 + data.length + 2];
		file[0] = 14;
		file[1] = 0x10; // protocol 1.0
		file[2] = (byte) PROFILE_VERSION;
		file[3] = (byte) (PROFILE_VERSION >> 8);
		file[4] = (byte) data.length;
		file[5] = (byte) (data.length >> 8);
		file[6] = (byte) (data.length >> 16);
		file[7] = (byte) (data.length >> 24);
		byte[] signature = ".FIT".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(signature, 0, file, 8, 4);
		int headerCrc = crc(file, 0, 12);
		file[12] = (byte) headerCrc;
		file[13] = (byte) (headerCrc >> 8);
		System.arraycopy(data, 0, file, 14, data.length);
		int crc = crc(file, 0, 14 + data.length);
		file[file.length - 2] = (byte) crc;
		file[file.length - 1] = (byte) (crc >> 8);
		return file;
	}

	/** The FIT CRC-16 over {@code length} bytes from {@code offset}. */
	public static int crc(byte[] bytes, int offset, int length) {
		int crc = 0;
		for (int i = offset; i < offset + length; i++) {
			int value = bytes[i] & 0xFF;
			int tmp = CRC_TABLE[crc & 0xF];
			crc = (crc >> 4) & 0x0FFF;
			crc = crc ^ tmp ^ CRC_TABLE[value & 0xF];
			tmp = CRC_TABLE[crc & 0xF];
			crc = (crc >> 4) & 0x0FFF;
			crc = crc ^ tmp ^ CRC_TABLE[(value >> 4) & 0xF];
		}
		return crc;
	}

	private static long scaled(double value, double scale) {
		return Math.max(0L, Math.min(INVALID_UINT32 - 1, Math.round(value * scale)));
	}

	private static int clamp(long value, int min, int max) {
		return (int) Math.max(min, Math.min(max, value));
	}

	private void string(String value, int size) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < size; i++) {
			this.out.write(i < bytes.length && i < size - 1 ? bytes[i] : 0);
		}
	}

	private void u8(int value) {
		this.out.write(value & 0xFF);
	}

	private void u16(int value) {
		this.out.write(value & 0xFF);
		this.out.write((value >> 8) & 0xFF);
	}

	private void s16(int value) {
		this.u16(value);
	}

	private void u32(long value) {
		this.out.write((int) (value & 0xFF));
		this.out.write((int) ((value >> 8) & 0xFF));
		this.out.write((int) ((value >> 16) & 0xFF));
		this.out.write((int) ((value >> 24) & 0xFF));
	}

	private void s32(int value) {
		this.u32(value & 0xFFFFFFFFL);
	}
}
