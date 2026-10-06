package dev.michidk.voxelvelo.track;

import java.util.*;

/** Standalone checks: deterministic curves, connected raster, flat terrain preservation and rideable terraces. */
public final class TrackChecks {
    public static void main(String[] args) {
        for (long seed : new long[] {0, 1, -19, Long.MAX_VALUE}) {
            var route = TrackGeometry.generate(seed, 124, -320, 10_000, 12, 0.85, 5);
            require(Math.abs(route.length() - 10_000) < 0.0001, "length scaling");
            require(route.centerline().getFirst().equals(new TrackGeometry.Point(124, -320)), "start at command origin");
            require(route.equals(TrackGeometry.generate(seed, 124, -320, 10_000, 12, 0.85, 5)), "seed reproducibility");
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
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
