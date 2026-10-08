package dev.michidk.voxvelo.track;

import java.util.*;

/** World-independent checkpoint circuit and terrace planner. Units are blocks/metres. */
public final class TrackGeometry {
	private TrackGeometry() {}
	public record Point(double x, double z) {}
	public record Cell(int x, int z) {}
	public record Route(List<Point> centerline, List<Cell> road, double length) {}
	record ChunkColumns(int x, int z, List<Cell> cells) {}
	private record Visit(Cell cell, double height) {}

	/** Visit each chunk once, retaining first-visit order along the circuit, including negative coordinates. */
	static List<ChunkColumns> byChunk(Collection<Cell> cells) {
		Map<Cell, List<Cell>> chunks = new LinkedHashMap<>();
		for (Cell cell : cells) {
			Cell chunk = new Cell(cell.x >> 4, cell.z >> 4);
			chunks.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(cell);
		}
		return chunks.entrySet().stream()
			.map(entry -> new ChunkColumns(entry.getKey().x, entry.getKey().z, List.copyOf(entry.getValue())))
			.toList();
	}

	public static Route generate(long seed, int originX, int originZ, double meters,
								int checkpoints, double bends, int width) {
		Random random = new Random(seed);
		double phase = random.nextDouble() * Math.PI * 2;
		List<Point> anchors = new ArrayList<>();
		for (int i = 0; i < checkpoints; i++) {
			double angle = 2 * Math.PI * i / checkpoints;
			double radius = 1 + bends * (0.27 * Math.sin(3 * angle + phase)
				+ 0.12 * Math.sin(5 * angle - phase) + 0.06 * (random.nextDouble() - 0.5));
			anchors.add(new Point(1.16 * radius * Math.cos(angle), 0.84 * radius * Math.sin(angle)));
		}
		List<Point> curve = new ArrayList<>();
		for (int i = 0; i < checkpoints; i++) {
			Point a = anchors.get((i + checkpoints - 1) % checkpoints);
			Point b = anchors.get(i), c = anchors.get((i + 1) % checkpoints);
			Point d = anchors.get((i + 2) % checkpoints);
			for (int j = 0; j < 64; j++) {
				double t = j / 64.0;
				curve.add(new Point(spline(a.x, b.x, c.x, d.x, t), spline(a.z, b.z, c.z, d.z, t)));
			}
		}
		double factor = meters / length(curve);
		Point start = curve.getFirst();
		List<Point> scaled = curve.stream().map(p -> new Point(originX + (p.x - start.x) * factor,
			originZ + (p.z - start.z) * factor)).toList();
		// Half-block sampling and overlapping disks produce a connected, full-width road at the seam too.
		Set<Cell> road = new LinkedHashSet<>();
		for (int i = 0; i < scaled.size(); i++) {
			Point a = scaled.get(i), b = scaled.get((i + 1) % scaled.size());
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x - a.x, b.z - a.z) * 2));
			for (int j = 0; j < steps; j++) {
				int x = (int) Math.round(a.x + (b.x - a.x) * j / steps);
				int z = (int) Math.round(a.z + (b.z - a.z) * j / steps);
				addRoadDisk(road, x, z, width);
			}
		}
		return new Route(scaled, List.copyOf(road), length(scaled));
	}

	/** Adds a circular road stamp whose block-aligned diameter is exactly {@code width}, for odd and even widths. */
	static void addRoadDisk(Set<Cell> road, int x, int z, int width) {
		int radius = width / 2;
		double offset = width % 2 == 0 ? 0.5 : 0.0;
		int low = (int) Math.ceil(offset - radius);
		int high = (int) Math.floor(offset + radius);
		double radiusSquared = radius * radius;
		for (int dx = low; dx <= high; dx++) {
			for (int dz = low; dz <= high; dz++) {
				double fromCentreX = dx - offset;
				double fromCentreZ = dz - offset;
				if (fromCentreX * fromCentreX + fromCentreZ * fromCentreZ <= radiusSquared) {
					road.add(new Cell(x + dx, z + dz));
				}
			}
		}
	}

	private static double spline(double a, double b, double c, double d, double t) {
		return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t
			+ (-a + 3 * b - 3 * c + d) * t * t * t);
	}

	public static double length(List<Point> points) {
		double length = 0;
		for (int i = 0; i < points.size(); i++) {
			Point a = points.get(i), b = points.get((i + 1) % points.size());
			length += Math.hypot(a.x - b.x, a.z - b.z);
		}
		return length;
	}

	/** Lipschitz envelopes bound grade across every road neighbor, including overlapping sections and the seam.
	 * Averaging upper/lower envelopes balances cutting and filling. Integer terraces differ by at most one block.
	 */
	public static Map<Cell, Integer> terrace(Map<Cell, Integer> terrain, double grade) {
		Map<Cell, Double> lower = envelope(terrain, grade, 1);
		Map<Cell, Double> negativeUpper = envelope(terrain, grade, -1);
		Map<Cell, Integer> result = new LinkedHashMap<>();
		terrain.forEach((cell, y) -> result.put(cell, (int) Math.round((lower.get(cell) - negativeUpper.get(cell)) / 2)));
		return result;
	}

	/**
	 * The road follows the terrain: the balanced, grade-limited surface of {@link #terrace}, so it cuts and fills as little
	 * as the grade allows. Over liquid it never drops below the liquid surface (a road must not dig a trench into a lake),
	 * so those cells set a grade-limited lower bound. The road at {@code start}, where the track was ordered, is exactly
	 * the terrain there, so whoever ordered it stands on the track; the grade limit shapes the road around that point.
	 */
	public static Map<Cell, Integer> follow(Map<Cell, Integer> surfaces, Set<Cell> liquid, double grade, Cell start) {
		Map<Cell, Integer> result = terrace(surfaces, grade);
		// Clamping a grade-limited surface between two grade-limited cones pins it at the start and keeps it grade-limited.
		Integer anchor = surfaces.get(start);
		if (anchor != null) {
			result.replaceAll((cell, y) -> {
				int reach = (int) Math.floor(grade * Math.hypot(cell.x - start.x, cell.z - start.z));
				return Math.clamp(y, anchor - reach, anchor + reach);
			});
		}
		if (!liquid.isEmpty()) {
			Map<Cell, Integer> wet = new LinkedHashMap<>();
			for (Cell cell : liquid) wet.put(cell, surfaces.get(cell));
			// The maximum of two grade-limited surfaces is grade-limited. Liquid wins over the start: no trench in a lake.
			embankment(wet, grade).forEach((cell, y) -> result.merge(cell, y, Math::max));
		}
		return result;
	}

	/** Riding surface in half-block units (not block Y). Water bounds propagate onto dry approaches too. */
	static Map<Cell, Integer> halfSteps(Map<Cell, Integer> surfaces, Set<Cell> liquid, double grade, Cell start) {
		Map<Cell, Integer> tops = new LinkedHashMap<>();
		surfaces.forEach((cell, y) -> tops.put(cell, 2 * (y + 1)));
		Map<Cell, Integer> result = follow(tops, Set.of(), grade * 2, start);
		if (!liquid.isEmpty()) {
			int minimum = Collections.min(tops.values()) - 1;
			Map<Cell, Integer> waterBounds = new LinkedHashMap<>();
			tops.forEach((cell, y) -> waterBounds.put(cell, liquid.contains(cell) ? y : minimum));
			embankment(waterBounds, grade * 2).forEach((cell, y) -> result.merge(cell, y, Math::max));
		}
		return result;
	}

	/** Smallest grade-limited embankment above every land/liquid surface. */
	public static Map<Cell, Integer> embankment(Map<Cell, Integer> surfaces, double grade) {
		Map<Cell, Double> upper = envelope(surfaces, grade, -1);
		Map<Cell, Integer> result = new LinkedHashMap<>();
		surfaces.forEach((cell, y) -> result.put(cell, (int) Math.ceil(-upper.get(cell) - 1e-9)));
		return result;
	}

	private static Map<Cell, Double> envelope(Map<Cell, Integer> terrain, double grade, int sign) {
		Map<Cell, Double> heights = new HashMap<>();
		PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::height)
			.thenComparingInt(v -> v.cell.x).thenComparingInt(v -> v.cell.z));
		terrain.forEach((cell, y) -> { heights.put(cell, (double) sign * y); queue.add(new Visit(cell, sign * y)); });
		while (!queue.isEmpty()) {
			Visit current = queue.remove();
			if (current.height > heights.get(current.cell)) continue;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					Cell next = new Cell(current.cell.x + dx, current.cell.z + dz);
					Double previous = heights.get(next);
					double candidate = current.height + grade * Math.hypot(dx, dz);
					if (previous != null && candidate < previous) {
						heights.put(next, candidate);
						queue.add(new Visit(next, candidate));
					}
				}
			}
		}
		return heights;
	}
}
