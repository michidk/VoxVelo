package dev.michidk.voxvelo.client.ride;

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
	/**
	 * Metres per degree on the mean-radius sphere (6,371,008.8 m), the model haversine distances use, so a site that
	 * measures the GPS track gets the same distance the ride recorded.
	 */
	private static final double METRES_PER_DEGREE = 6_371_008.8 * Math.PI / 180.0;

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

	private static final int FILE_TYPE_ACTIVITY = 4;
	private static final int ACTIVITY_TYPE_MANUAL = 0;
	private static final int SPORT_CYCLING = 2;
	private static final int SUB_SPORT_VIRTUAL_ACTIVITY = 58;
	private static final int EVENT_TIMER = 0;
	private static final int EVENT_LAP = 9;
	private static final int EVENT_SESSION = 8;
	private static final int EVENT_ACTIVITY = 26;
	private static final int EVENT_TYPE_START = 0;
	private static final int EVENT_TYPE_STOP = 1;
	private static final int EVENT_TYPE_STOP_ALL = 4;

	// Field numbers. Every message keeps its timestamp and message index at the same numbers.
	private static final int FIELD_TIMESTAMP = 253;
	private static final int FIELD_MESSAGE_INDEX = 254;

	private static final class FileIdField {
		static final int TYPE = 0;
		static final int MANUFACTURER = 1;
		static final int PRODUCT = 2;
		static final int SERIAL_NUMBER = 3;
		static final int TIME_CREATED = 4;
		static final int PRODUCT_NAME = 8;
	}

	private static final class EventField {
		static final int EVENT = 0;
		static final int EVENT_TYPE = 1;
	}

	private static final class RecordField {
		static final int POSITION_LAT = 0;
		static final int POSITION_LONG = 1;
		static final int ALTITUDE = 2;
		static final int HEART_RATE = 3;
		static final int CADENCE = 4;
		static final int DISTANCE = 5;
		static final int SPEED = 6;
		static final int POWER = 7;
		static final int GRADE = 9;
	}

	private static final class LapField {
		static final int EVENT = 0;
		static final int EVENT_TYPE = 1;
		static final int START_TIME = 2;
		static final int START_POSITION_LAT = 3;
		static final int START_POSITION_LONG = 4;
		static final int END_POSITION_LAT = 5;
		static final int END_POSITION_LONG = 6;
		static final int TOTAL_ELAPSED_TIME = 7;
		static final int TOTAL_TIMER_TIME = 8;
		static final int TOTAL_DISTANCE = 9;
		static final int TOTAL_CALORIES = 11;
		static final int AVG_SPEED = 13;
		static final int MAX_SPEED = 14;
		static final int AVG_HEART_RATE = 15;
		static final int MAX_HEART_RATE = 16;
		static final int AVG_CADENCE = 17;
		static final int MAX_CADENCE = 18;
		static final int AVG_POWER = 19;
		static final int MAX_POWER = 20;
		static final int TOTAL_ASCENT = 21;
		static final int TOTAL_DESCENT = 22;
		static final int SPORT = 25;
		static final int SUB_SPORT = 39;
	}

	private static final class SessionField {
		static final int EVENT = 0;
		static final int EVENT_TYPE = 1;
		static final int START_TIME = 2;
		static final int START_POSITION_LAT = 3;
		static final int START_POSITION_LONG = 4;
		static final int SPORT = 5;
		static final int SUB_SPORT = 6;
		static final int TOTAL_ELAPSED_TIME = 7;
		static final int TOTAL_TIMER_TIME = 8;
		static final int TOTAL_DISTANCE = 9;
		static final int TOTAL_CALORIES = 11;
		static final int AVG_SPEED = 14;
		static final int MAX_SPEED = 15;
		static final int AVG_HEART_RATE = 16;
		static final int MAX_HEART_RATE = 17;
		static final int AVG_CADENCE = 18;
		static final int MAX_CADENCE = 19;
		static final int AVG_POWER = 20;
		static final int MAX_POWER = 21;
		static final int TOTAL_ASCENT = 22;
		static final int TOTAL_DESCENT = 23;
		static final int FIRST_LAP_INDEX = 25;
		static final int NUM_LAPS = 26;
		static final int NORMALIZED_POWER = 34;
	}

	private static final class ActivityField {
		static final int TOTAL_TIMER_TIME = 0;
		static final int NUM_SESSIONS = 1;
		static final int TYPE = 2;
		static final int EVENT = 3;
		static final int EVENT_TYPE = 4;
		static final int LOCAL_TIMESTAMP = 5;
	}

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

	/** Where the ride started and finished, in semicircles; invalid without a position or when it ends in another dimension. */
	private record Ends(int startLat, int startLon, int endLat, int endLon) {
		static final Ends NONE = new Ends(INVALID_SINT32, INVALID_SINT32, INVALID_SINT32, INVALID_SINT32);
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
		// Without samples the timer never ran, so the file ends where it starts.
		long end = samples.isEmpty() ? start : Math.max(start, fitTime(samples.getLast().timeMillis()));
		RideSample origin = samples.isEmpty() ? null : samples.getFirst();
		boolean position = options.includePosition() && origin != null;

		this.writeFileId(ride, start);
		this.writeRecords(samples, start, end, origin, position, options);
		Ends ends = position ? ends(samples.getLast(), origin, options) : Ends.NONE;
		this.writeLap(summary, start, end, ends);
		this.writeSession(summary, start, end, ends);
		this.writeActivity(summary, end, options);
		return this.finish();
	}

	private static Ends ends(RideSample last, RideSample origin, Options options) {
		double[] first = toLatLon(0, 0, options);
		if (!last.dimension().equals(origin.dimension())) {
			return new Ends(semicircles(first[0]), semicircles(first[1]), INVALID_SINT32, INVALID_SINT32);
		}
		double[] ll = toLatLon(last.x() - origin.x(), last.z() - origin.z(), options);
		return new Ends(semicircles(first[0]), semicircles(first[1]), semicircles(ll[0]), semicircles(ll[1]));
	}

	private void writeFileId(RideRecording ride, long start) {
		this.define(LOCAL_FILE_ID, MESG_FILE_ID,
			new Field(FileIdField.TYPE, 1, ENUM), new Field(FileIdField.MANUFACTURER, 2, UINT16),
			new Field(FileIdField.PRODUCT, 2, UINT16), new Field(FileIdField.SERIAL_NUMBER, 4, UINT32Z),
			new Field(FileIdField.TIME_CREATED, 4, UINT32), new Field(FileIdField.PRODUCT_NAME, PRODUCT_NAME_SIZE, STRING));
		this.header(LOCAL_FILE_ID);
		this.u8(FILE_TYPE_ACTIVITY);
		this.u16(MANUFACTURER_DEVELOPMENT);
		this.u16(1);
		this.u32((ride.startMillis() & 0x7FFFFFFFL) | 1L);
		this.u32(start);
		this.string("Voxel Fitness", PRODUCT_NAME_SIZE);
	}

	/** The timer start, one record per sample with a timer stop and start around every interruption, and the final stop. */
	private void writeRecords(List<RideSample> samples, long start, long end, RideSample origin, boolean position, Options options) {
		this.define(LOCAL_EVENT, MESG_EVENT, new Field(FIELD_TIMESTAMP, 4, UINT32),
			new Field(EventField.EVENT, 1, ENUM), new Field(EventField.EVENT_TYPE, 1, ENUM));
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
	}

	private void writeLap(RideSummary summary, long start, long end, Ends ends) {
		this.define(LOCAL_LAP, MESG_LAP,
			new Field(FIELD_TIMESTAMP, 4, UINT32), new Field(LapField.EVENT, 1, ENUM), new Field(LapField.EVENT_TYPE, 1, ENUM),
			new Field(LapField.START_TIME, 4, UINT32), new Field(LapField.START_POSITION_LAT, 4, SINT32),
			new Field(LapField.START_POSITION_LONG, 4, SINT32), new Field(LapField.END_POSITION_LAT, 4, SINT32),
			new Field(LapField.END_POSITION_LONG, 4, SINT32), new Field(LapField.TOTAL_ELAPSED_TIME, 4, UINT32),
			new Field(LapField.TOTAL_TIMER_TIME, 4, UINT32), new Field(LapField.TOTAL_DISTANCE, 4, UINT32),
			new Field(LapField.TOTAL_CALORIES, 2, UINT16), new Field(LapField.AVG_SPEED, 2, UINT16),
			new Field(LapField.MAX_SPEED, 2, UINT16), new Field(LapField.AVG_HEART_RATE, 1, UINT8),
			new Field(LapField.MAX_HEART_RATE, 1, UINT8), new Field(LapField.AVG_CADENCE, 1, UINT8),
			new Field(LapField.MAX_CADENCE, 1, UINT8), new Field(LapField.AVG_POWER, 2, UINT16),
			new Field(LapField.MAX_POWER, 2, UINT16), new Field(LapField.TOTAL_ASCENT, 2, UINT16),
			new Field(LapField.TOTAL_DESCENT, 2, UINT16), new Field(LapField.SPORT, 1, ENUM),
			new Field(LapField.SUB_SPORT, 1, ENUM), new Field(FIELD_MESSAGE_INDEX, 2, UINT16));
		this.header(LOCAL_LAP);
		this.u32(end);
		this.u8(EVENT_LAP);
		this.u8(EVENT_TYPE_STOP);
		this.u32(start);
		this.s32(ends.startLat());
		this.s32(ends.startLon());
		this.s32(ends.endLat());
		this.s32(ends.endLon());
		this.totals(summary);
		this.u8(SPORT_CYCLING);
		this.u8(SUB_SPORT_VIRTUAL_ACTIVITY);
		this.u16(0); // message index
	}

	private void writeSession(RideSummary summary, long start, long end, Ends ends) {
		this.define(LOCAL_SESSION, MESG_SESSION,
			new Field(FIELD_TIMESTAMP, 4, UINT32), new Field(SessionField.EVENT, 1, ENUM), new Field(SessionField.EVENT_TYPE, 1, ENUM),
			new Field(SessionField.START_TIME, 4, UINT32), new Field(SessionField.START_POSITION_LAT, 4, SINT32),
			new Field(SessionField.START_POSITION_LONG, 4, SINT32), new Field(SessionField.SPORT, 1, ENUM),
			new Field(SessionField.SUB_SPORT, 1, ENUM), new Field(SessionField.TOTAL_ELAPSED_TIME, 4, UINT32),
			new Field(SessionField.TOTAL_TIMER_TIME, 4, UINT32), new Field(SessionField.TOTAL_DISTANCE, 4, UINT32),
			new Field(SessionField.TOTAL_CALORIES, 2, UINT16), new Field(SessionField.AVG_SPEED, 2, UINT16),
			new Field(SessionField.MAX_SPEED, 2, UINT16), new Field(SessionField.AVG_HEART_RATE, 1, UINT8),
			new Field(SessionField.MAX_HEART_RATE, 1, UINT8), new Field(SessionField.AVG_CADENCE, 1, UINT8),
			new Field(SessionField.MAX_CADENCE, 1, UINT8), new Field(SessionField.AVG_POWER, 2, UINT16),
			new Field(SessionField.MAX_POWER, 2, UINT16), new Field(SessionField.TOTAL_ASCENT, 2, UINT16),
			new Field(SessionField.TOTAL_DESCENT, 2, UINT16), new Field(SessionField.FIRST_LAP_INDEX, 2, UINT16),
			new Field(SessionField.NUM_LAPS, 2, UINT16), new Field(SessionField.NORMALIZED_POWER, 2, UINT16),
			new Field(FIELD_MESSAGE_INDEX, 2, UINT16));
		this.header(LOCAL_SESSION);
		this.u32(end);
		this.u8(EVENT_SESSION);
		this.u8(EVENT_TYPE_STOP);
		this.u32(start);
		this.s32(ends.startLat());
		this.s32(ends.startLon());
		this.u8(SPORT_CYCLING);
		this.u8(SUB_SPORT_VIRTUAL_ACTIVITY);
		this.totals(summary);
		this.u16(0); // first lap index
		this.u16(1); // number of laps
		this.u16(summary.normalizedPower() >= 0 ? summary.normalizedPower() : INVALID_UINT16);
		this.u16(0); // message index
	}

	private void writeActivity(RideSummary summary, long end, Options options) {
		this.define(LOCAL_ACTIVITY, MESG_ACTIVITY,
			new Field(FIELD_TIMESTAMP, 4, UINT32), new Field(ActivityField.TOTAL_TIMER_TIME, 4, UINT32),
			new Field(ActivityField.NUM_SESSIONS, 2, UINT16), new Field(ActivityField.TYPE, 1, ENUM),
			new Field(ActivityField.EVENT, 1, ENUM), new Field(ActivityField.EVENT_TYPE, 1, ENUM),
			new Field(ActivityField.LOCAL_TIMESTAMP, 4, UINT32));
		this.header(LOCAL_ACTIVITY);
		this.u32(end);
		this.u32(scaled(summary.timerSeconds(), 1000));
		this.u16(1); // number of sessions
		this.u8(ACTIVITY_TYPE_MANUAL);
		this.u8(EVENT_ACTIVITY);
		this.u8(EVENT_TYPE_STOP);
		this.u32(end + options.utcOffsetSeconds());
	}

	private static Field[] recordFields(boolean position) {
		Field timestamp = new Field(FIELD_TIMESTAMP, 4, UINT32);
		Field altitude = new Field(RecordField.ALTITUDE, 2, UINT16);
		Field heartRate = new Field(RecordField.HEART_RATE, 1, UINT8);
		Field cadence = new Field(RecordField.CADENCE, 1, UINT8);
		Field distance = new Field(RecordField.DISTANCE, 4, UINT32);
		Field speed = new Field(RecordField.SPEED, 2, UINT16);
		Field power = new Field(RecordField.POWER, 2, UINT16);
		Field grade = new Field(RecordField.GRADE, 2, SINT16);
		if (position) {
			return new Field[] {timestamp, new Field(RecordField.POSITION_LAT, 4, SINT32), new Field(RecordField.POSITION_LONG, 4, SINT32),
				altitude, heartRate, cadence, distance, speed, power, grade};
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
