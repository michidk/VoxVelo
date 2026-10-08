package dev.michidk.voxvelo.track;

import java.util.*;

/** Standalone checks: deterministic curves, connected raster, flat terrain preservation and rideable terraces. */
public final class TrackChecks {
	public static void main(String[] args) {
		TrackStructureChecks.run();
		TrackTreeChecks.run();
		var boundaryCells = List.of(new TrackGeometry.Cell(-17, 16), new TrackGeometry.Cell(0, 0),
			new TrackGeometry.Cell(-16, -1), new TrackGeometry.Cell(15, 15),
			new TrackGeometry.Cell(-32, 31), new TrackGeometry.Cell(16, -16));
		var batches = TrackGeometry.byChunk(boundaryCells);
		require(batches.size() == 4, "one batch per chunk across positive and negative boundaries");
		require(batches.getFirst().x() == -2 && batches.getFirst().z() == 1,
			"chunk batches follow first visit order");
		require(batches.getFirst().cells().equals(List.of(boundaryCells.get(0), boundaryCells.get(4))),
			"nonconsecutive columns in the same chunk are visited together");
		require(TrackGeometry.byChunk(List.of()).isEmpty(), "empty chunk plan");
		for (int width = 3; width <= 11; width++) {
			Set<TrackGeometry.Cell> footprint = new HashSet<>();
			TrackGeometry.addRoadDisk(footprint, 0, 0, width);
			int minX = footprint.stream().mapToInt(TrackGeometry.Cell::x).min().orElseThrow();
			int maxX = footprint.stream().mapToInt(TrackGeometry.Cell::x).max().orElseThrow();
			int minZ = footprint.stream().mapToInt(TrackGeometry.Cell::z).min().orElseThrow();
			int maxZ = footprint.stream().mapToInt(TrackGeometry.Cell::z).max().orElseThrow();
			require(maxX - minX + 1 == width && maxZ - minZ + 1 == width,
				"requested odd or even road width is preserved");
		}
		for (long seed : new long[] {0, 1, -19, Long.MAX_VALUE}) {
			var route = TrackGeometry.generate(seed, 124, -320, 10_000, 12, 0.85, 6);
			require(Math.abs(route.length() - 10_000) < 0.0001, "length scaling");
			require(route.centerline().getFirst().equals(new TrackGeometry.Point(124, -320)), "start at command origin");
			require(route.equals(TrackGeometry.generate(seed, 124, -320, 10_000, 12, 0.85, 6)), "seed reproducibility");
			Set<TrackGeometry.Cell> batched = new HashSet<>();
			Set<TrackGeometry.Cell> chunkPositions = new HashSet<>();
			for (var batch : TrackGeometry.byChunk(route.road())) {
				require(chunkPositions.add(new TrackGeometry.Cell(batch.x(), batch.z())), "each chunk visited once");
				for (var cell : batch.cells()) {
					require(cell.x() >> 4 == batch.x() && cell.z() >> 4 == batch.z(), "column belongs to its loaded chunk");
					require(batched.add(cell), "each road column visited once");
				}
			}
			require(batched.equals(new HashSet<>(route.road())), "batching preserves the full road footprint");
			Set<TrackGeometry.Cell> unseen = new HashSet<>(route.road());
			Deque<TrackGeometry.Cell> pending = new ArrayDeque<>();
			pending.add(route.road().getFirst());
			unseen.remove(pending.getFirst());
			while (!pending.isEmpty()) {
				var cell = pending.removeFirst();
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					var next = new TrackGeometry.Cell(cell.x() + d[0], cell.z() + d[1]);
					if (unseen.remove(next)) pending.add(next);
				}
			}
			require(unseen.isEmpty(), "road is four-connected including closure");
		}
		Map<TrackGeometry.Cell, Integer> terrain = new LinkedHashMap<>();
		for (int x = 0; x < 240; x++) for (int z = 0; z < 5; z++)
			terrain.put(new TrackGeometry.Cell(x, z), 70);
		require(TrackGeometry.terrace(terrain, 0.08).equals(terrain), "flat ground unchanged");
		terrain.replaceAll((cell, y) -> cell.x() < 100 ? 64 : 100);
		var heights = TrackGeometry.terrace(terrain, 0.08);
		for (var a : terrain.keySet()) {
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				var b = new TrackGeometry.Cell(a.x() + dx, a.z() + dz);
				if (heights.containsKey(b)) require(Math.abs(heights.get(a) - heights.get(b)) <= 1, "bike step limit across width and diagonals");
			}
			var b = new TrackGeometry.Cell(a.x() + 50, a.z());
			if (heights.containsKey(b)) require(Math.abs(heights.get(a) - heights.get(b)) <= 5, "8% grade plus rounding over 50m");
		}
		var loop = TrackGeometry.generate(82, 0, 0, 250, 24, 1, 3);
		terrain.clear();
		for (var cell : loop.road()) terrain.put(cell, cell.x() < 0 ? 80 : 110);
		heights = TrackGeometry.terrace(terrain, 0.08);
		for (var cell : loop.road()) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			var next = new TrackGeometry.Cell(cell.x() + dx, cell.z() + dz);
			if (heights.containsKey(next)) require(Math.abs(heights.get(cell) - heights.get(next)) <= 1, "loop seam and steep terrain");
		}
		System.out.println("Track geometry and grading checks passed.");
		// Water/lava surfaces use the same height constraint; a flat crossing stays at surface level.
		for (int liquidLevel : new int[] {31, 62}) {
			terrain.replaceAll((cell, y) -> liquidLevel);
			require(TrackGeometry.embankment(terrain, 0.08).equals(terrain), "flat liquid crossing stays at surface level");
		}
		terrain.replaceAll((cell, y) -> cell.x() < 0 ? 31 : 180);
		heights = TrackGeometry.embankment(terrain, 0.08);
		for (var cell : terrain.keySet()) {
			require(heights.get(cell) >= terrain.get(cell), "road never goes below the liquid/ground surface");
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				var next = new TrackGeometry.Cell(cell.x() + dx, cell.z() + dz);
				if (heights.containsKey(next)) require(Math.abs(heights.get(cell) - heights.get(next)) <= 1,
					"large shore/cliff transition remains rideable at the loop seam and across the road");
			}
		}
		System.out.println("Liquid surface and embankment checks passed.");
		// The road follows the terrain, starts exactly on it, never sinks into liquid and stays rideable.
		var hills = new LinkedHashMap<TrackGeometry.Cell, Integer>();
		var wet = new HashSet<TrackGeometry.Cell>();
		for (int x = -80; x <= 80; x++) for (int z = -3; z <= 3; z++) {
			var cell = new TrackGeometry.Cell(x, z);
			hills.put(cell, 70 + (int) Math.round(14 * Math.sin(x / 12.0)));
			if (x > 30) { hills.put(cell, 62); wet.add(cell); }
		}
		var start = new TrackGeometry.Cell(0, 0);
		var followed = TrackGeometry.follow(hills, wet, 0.08, start);
		var reordered = new LinkedHashMap<TrackGeometry.Cell, Integer>();
		for (var batch : TrackGeometry.byChunk(hills.keySet()))
			for (var cell : batch.cells()) reordered.put(cell, hills.get(cell));
		require(TrackGeometry.follow(reordered, wet, 0.08, start).equals(followed),
			"chunk survey order preserves planned road heights");
		require(followed.get(start).equals(hills.get(start)), "road starts exactly on the terrain");
		for (var cell : hills.keySet()) {
			if (wet.contains(cell)) require(followed.get(cell) >= hills.get(cell), "road stays above liquid");
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				var next = new TrackGeometry.Cell(cell.x() + dx, cell.z() + dz);
				if (followed.containsKey(next)) require(Math.abs(followed.get(cell) - followed.get(next)) <= 1, "followed road is rideable");
			}
		}
		System.out.println("Terrain-following checks passed.");
	}
	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
