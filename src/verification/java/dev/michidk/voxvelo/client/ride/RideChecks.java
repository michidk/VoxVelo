package dev.michidk.voxvelo.client.ride;

import dev.michidk.voxvelo.fitness.RideMapLayout;
import dev.michidk.voxvelo.network.RideMapPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone checks for ride recording: the summary numbers, the FIT encoding (decoded again by a small reader
 * here), the fake GPS mapping, route thinning and the ride map layout. With a path argument the sample FIT file is
 * also written there, for checking it with other FIT tools.
 */
public final class RideChecks {
	private static final long T0 = 1_791_288_000_000L; // 2026-10-06T12:00:00Z

	public static void main(String[] args) throws IOException {
		RideRecording ride = sampleRide();
		RideSummary summary = ride.summary();
		require(summary.timerSeconds() == 598, "riding time leaves out the dismounted minute: " + summary.timerSeconds());
		require(summary.elapsedSeconds() == 700, "elapsed time is the whole session");
		require(Math.abs(summary.distanceM() - 598 * 8.0) < 1e-6, "distance is the ridden path: " + summary.distanceM());
		require(Math.abs(summary.avgSpeedMs() - 8.0) < 1e-6, "average speed is distance over moving time");
		require(summary.avgPowerWatts() == 200 && summary.maxPowerWatts() == 200, "average and maximum power");
		require(summary.normalizedPower() == 200, "steady power normalizes to itself");
		require(Math.abs(summary.workKj() - 119.6) < 1e-6 && summary.calories() == 120, "work counts riding time only: " + summary.workKj());
		require(summary.avgCadenceRpm() == 90 && summary.avgHeartRate() == 140 && summary.maxHeartRate() == 150, "cadence and heart rate");
		require(summary.ascentM() == 20 && summary.descentM() == 20, "ascent and descent follow the climb: " + summary.ascentM() + "/" + summary.descentM());
		require(RideSummary.normalizedPower(ride.samples().subList(0, 29)) == -1, "no normalized power under 30 seconds");

		List<RideSample> intervals = new ArrayList<>();
		for (int i = 0; i < 120; i++) {
			intervals.add(sample(i, i, 0, 0, i < 60 ? 100 : 300, false));
		}
		int np = RideSummary.normalizedPower(intervals);
		require(np > 200 && np < 300, "uneven power normalizes above its average: " + np);

		FitWriter.Options options = new FitWriter.Options(true, 30.0, -40.0, 7200);
		double[] east = FitWriter.toLatLon(1000, 0, options);
		double[] south = FitWriter.toLatLon(0, 1000, options);
		require(Math.abs(east[0] - 30.0) < 1e-9 && Math.abs(east[1] - (-40.0 + 1000 / ((6_371_008.8 * Math.PI / 180.0) * Math.cos(Math.toRadians(30))))) < 1e-9, "+X is east");
		require(Math.abs(south[0] - (30.0 - 1000 / (6_371_008.8 * Math.PI / 180.0))) < 1e-9 && Math.abs(south[1] + 40.0) < 1e-9, "+Z is south");
		require(FitWriter.semicircles(90.0) == 1 << 30 && FitWriter.semicircles(-45.0) == -(1 << 29), "semicircles");
		require(FitWriter.fitTime(T0) == T0 / 1000 - 631_065_600L, "FIT epoch");

		byte[] fit = FitWriter.write(ride, options);
		Map<Integer, List<Map<Integer, Long>>> messages = decode(fit);
		require(messages.get(0).size() == 1 && messages.get(0).getFirst().get(0) == 4, "one activity file_id");
		require(messages.get(20).size() == ride.samples().size(), "one record per sample");
		require(messages.get(21).size() == 4, "timer start, stop and start around the gap, final stop");
		Map<Integer, Long> session = messages.get(18).getFirst();
		require(session.get(5) == 2 && session.get(6) == 58, "a virtual cycling session");
		require(session.get(8) == 598_000 && session.get(7) == 700_000, "session timer and elapsed time in ms");
		require(session.get(9) == 478_400, "session distance in cm");
		require(session.get(20) == 200 && session.get(34) == 200 && session.get(16) == 140, "session power and heart rate");
		require(messages.get(19).size() == 1 && messages.get(34).size() == 1, "one lap, one activity");
		Map<Integer, Long> first = messages.get(20).getFirst();
		require(first.get(0) == FitWriter.semicircles(30.0) && first.get(1) == (FitWriter.semicircles(-40.0) & 0xFFFFFFFFL), "the ride starts at the origin");
		require(first.get(2) == Math.round((64 + 500) * 5.0), "altitude is the block height");
		Map<Integer, Long> last = messages.get(20).getLast();
		require(last.get(5) == 478_400 && last.get(6) == 8_000 && last.get(7) == 200 && last.get(3) == 140, "record values and scales");

		byte[] indoor = FitWriter.write(ride, new FitWriter.Options(false, 30.0, -40.0, 0));
		require(!decode(indoor).get(20).getFirst().containsKey(0), "without GPS the records have no position");
		byte[] empty = FitWriter.write(new RideRecording(T0, T0 + 5_000, List.of()), options);
		Map<Integer, List<Map<Integer, Long>>> emptyMessages = decode(empty);
		require(emptyMessages.get(18).size() == 1, "an empty ride is still a valid file");
		require(emptyMessages.get(21).getFirst().get(253).equals(emptyMessages.get(21).getLast().get(253)), "an empty ride's timer never runs");

		List<RideSample> lots = new ArrayList<>();
		for (int i = 0; i < 5_000; i++) {
			lots.add(sample(i, i, 0, i, 200, i == 2_500));
		}
		List<RideMapPayload.Point> route = BicycleRideExport.routePoints(lots, RideMapPayload.MAX_POINTS);
		require(route.size() <= RideMapPayload.MAX_POINTS && route.size() > RideMapPayload.MAX_POINTS / 2, "route is thinned to the limit: " + route.size());
		require(route.getFirst().x() == 0 && route.getLast().x() == 4_999, "both ends are kept");
		require(route.stream().filter(RideMapPayload.Point::newStretch).count() == 1
			&& route.stream().anyMatch(point -> point.newStretch() && point.x() == 2_500)
			&& route.stream().anyMatch(point -> point.x() == 2_499), "the interruption stays a gap with both ends kept");
		require(9 * RideMapPayload.MAX_POINTS + 400 < 32_767, "the largest map request fits a serverbound custom payload");
		checkInterruptedRoutes();

		RideMapLayout small = RideMapLayout.fit(List.of(point(10, 10), point(110, 60)));
		require(small.aligned() && small.scale() == 0 && small.centerX() == 60 && small.centerZ() == 35, "a short ride is a scale 0 map on the route");
		RideMapLayout medium = RideMapLayout.fit(List.of(point(-500, 0), point(500, 0)));
		require(medium.aligned() && medium.scale() == 4 && medium.centerX() % 16 == 0, "1 km needs scale 4 (scale 3 covers 928 blocks inside the margin)");
		RideMapLayout large = RideMapLayout.fit(List.of(point(0, 0), point(10_000, 3_000)));
		require(!large.aligned(), "a 10 km ride is fitted, not aligned");
		for (RideMapPayload.Point corner : List.of(point(0, 0), point(10_000, 3_000), point(0, 3_000))) {
			int px = large.pixelX(corner.x());
			int pz = large.pixelZ(corner.z());
			require(px >= 5 && px <= 122 && pz >= 5 && pz <= 122, "every point lands on the map: " + px + "," + pz);
		}
		byte[] colors = new byte[RideMapLayout.SIZE * RideMapLayout.SIZE];
		List<RideMapPayload.Point> square = List.of(point(0, 0), point(100, 0), point(100, 100), point(0, 100), point(0, 0));
		RideMapLayout squareLayout = RideMapLayout.fit(square);
		squareLayout.drawRoute(colors, square, (byte) 1, (byte) 2, (byte) 3);
		require(colors[squareLayout.pixelX(0) + squareLayout.pixelZ(0) * RideMapLayout.SIZE] == 2, "the start is marked");
		require(colors[squareLayout.pixelX(50) + squareLayout.pixelZ(0) * RideMapLayout.SIZE] == 1, "the line is drawn between points");
		require(colors[squareLayout.pixelX(50) + squareLayout.pixelZ(50) * RideMapLayout.SIZE] == 0, "the inside of the loop stays paper");
		boolean finish = false;
		for (byte color : colors) {
			finish |= color == 3;
		}
		require(!finish, "a loop has no separate finish marker");

		if (args.length > 0) {
			Path out = Path.of(args[0]);
			Files.createDirectories(out.toAbsolutePath().getParent());
			Files.write(out, fit);
			System.out.println("Sample FIT file written to " + out.toAbsolutePath());
		}
		System.out.println("Ride checks passed.");
	}

	/** Ten minutes of riding at 8 m/s and 200 W with a dismounted minute, climbing 20 blocks and back. */
	private static RideRecording sampleRide() {
		List<RideSample> samples = new ArrayList<>();
		double distance = 0;
		for (int second = 0; second < 660; second++) {
			if (second >= 300 && second < 360) {
				continue;
			}
			boolean resumed = second == 360;
			if (!samples.isEmpty() && !resumed) {
				distance += 8.0;
			}
			int riding = second < 300 ? second : second - 60;
			double y = 64 + (riding < 300 ? Math.min(20, riding / 10) : Math.max(0, 20 - (riding - 300) / 10));
			int heartRate = second == 100 ? 150 : 140;
			samples.add(new RideSample(T0 + second * 1000L, distance, y, 0, distance, 8.0, 200, 90, heartRate, 0.05, "minecraft:overworld", resumed));
		}
		return new RideRecording(T0, T0 + 700_000, samples);
	}

	private static RideSample sample(int second, double x, double y, double z, int power, boolean resumed) {
		return new RideSample(T0 + second * 1000L, x, y, z, x, 5.0, power, 85, 0, 0.0, "minecraft:overworld", resumed);
	}

	private static RideMapPayload.Point point(int x, int z) {
		return new RideMapPayload.Point(x, z, false);
	}

	/** A small FIT reader: checks both CRCs and returns every message as field number to raw unsigned value. */
	private static Map<Integer, List<Map<Integer, Long>>> decode(byte[] file) {
		require(file[0] == 14 && new String(file, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals(".FIT"), "FIT header");
		require(FitWriter.crc(file, 0, 12) == ((file[12] & 0xFF) | (file[13] & 0xFF) << 8), "header CRC");
		int size = (int) little(file, 4, 4);
		require(size == file.length - 16, "data size");
		require(FitWriter.crc(file, 0, file.length) == 0, "file CRC");
		Map<Integer, int[][]> definitions = new HashMap<>();
		Map<Integer, Integer> globals = new HashMap<>();
		Map<Integer, List<Map<Integer, Long>>> messages = new HashMap<>();
		for (int global : new int[] {0, 18, 19, 20, 21, 34}) {
			messages.put(global, new ArrayList<>());
		}
		int pos = 14;
		while (pos < 14 + size) {
			int header = file[pos++] & 0xFF;
			require((header & 0x80) == 0, "normal headers only");
			int local = header & 0x0F;
			if ((header & 0x40) != 0) {
				require(file[pos + 1] == 0, "little endian");
				int global = (int) little(file, pos + 2, 2);
				int count = file[pos + 4] & 0xFF;
				pos += 5;
				int[][] fields = new int[count][];
				for (int i = 0; i < count; i++, pos += 3) {
					fields[i] = new int[] {file[pos] & 0xFF, file[pos + 1] & 0xFF, file[pos + 2] & 0xFF};
				}
				definitions.put(local, fields);
				globals.put(local, global);
			} else {
				int[][] fields = definitions.get(local);
				require(fields != null, "data before its definition");
				Map<Integer, Long> values = new HashMap<>();
				for (int[] field : fields) {
					if (field[2] != 0x07) {
						long value = little(file, pos, field[1]);
						boolean invalid = value == (field[1] == 1 ? 0xFFL : field[1] == 2 ? (field[2] == 0x83 ? 0x7FFFL : 0xFFFFL)
							: field[2] == 0x85 ? 0x7FFFFFFFL : 0xFFFFFFFFL);
						if (!invalid) {
							values.put(field[0], value);
						}
					}
					pos += field[1];
				}
				messages.get(globals.get(local)).add(values);
			}
		}
		require(pos == 14 + size, "messages end where the data ends");
		return messages;
	}

	private static void checkInterruptedRoutes() {
		List<RideSample> fragmented = new ArrayList<>();
		for (int i = 0; i < RideRecorder.MAX_SAMPLES; i++) {
			fragmented.add(sample(i, i, 0, i, 200, i > 0 && i % 3 == 0));
		}
		long started = System.nanoTime();
		List<RideMapPayload.Point> route = BicycleRideExport.routePoints(fragmented, RideMapPayload.MAX_POINTS);
		require(System.nanoTime() - started < 5_000_000_000L, "a full day of interrupted riding must thin within five seconds");
		require(route.size() == RideMapPayload.MAX_POINTS, "endpoint overflow fills the point budget");
		for (int i = 0; i < route.size(); i++) {
			RideMapPayload.Point point = route.get(i);
			require(point.x() == (i / 2) * 3 + (i % 2) * 2, "overflow retains the first stretch endpoints in order");
			require(point.newStretch() == (i > 0 && i % 2 == 0), "overflow never joins interrupted stretches");
		}
		List<RideSample> shortRide = fragmented.subList(0, 9);
		List<RideMapPayload.Point> endpoints = BicycleRideExport.routePoints(shortRide, 6);
		require(endpoints.stream().map(RideMapPayload.Point::x).toList().equals(List.of(0, 2, 3, 5, 6, 8)),
			"an exact endpoint budget preserves every stretch");
		List<RideMapPayload.Point> withInterior = BicycleRideExport.routePoints(shortRide, 7);
		require(withInterior.size() == 7 && withInterior.containsAll(endpoints), "spare slots preserve all endpoints");
		require(BicycleRideExport.routePoints(shortRide, 1).getFirst().x() == 0, "a one-point budget keeps the start");
		require(BicycleRideExport.routePoints(shortRide, 0).isEmpty(), "a zero budget returns no points");
		require(BicycleRideExport.routePoints(shortRide, -1).isEmpty(), "a negative budget returns no points");
		require(BicycleRideExport.routePoints(List.of(), 10).isEmpty(), "an empty ride returns no points");
		require(BicycleRideExport.routePoints(shortRide, 20).size() == shortRide.size(), "short routes need no thinning");
	}

	private static long little(byte[] bytes, int offset, int length) {
		long value = 0;
		for (int i = length - 1; i >= 0; i--) {
			value = value << 8 | (bytes[offset + i] & 0xFF);
		}
		return value;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
