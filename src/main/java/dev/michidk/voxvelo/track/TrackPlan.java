package dev.michidk.voxvelo.track;

import dev.michidk.voxvelo.track.TrackGeometry.Cell;
import java.util.*;

/** World-independent structure plan. Borders occupy the surveyed shoulder, never the riding surface. */
final class TrackPlan {
	/** Blocks from the deck up to a tunnel's roof; terrain that reaches as high is bored through. */
	private static final int TUNNEL_HEIGHT = 6;
	/** Fill under the road, in half blocks, from which it is carried on a bridge instead of an embankment. */
	private static final int BRIDGE_FILL_HALVES = 6;

	enum Kind { GROUND, BRIDGE, TUNNEL }
	record Column(int topHalf, Kind kind, boolean border, boolean light, boolean pier) {
		int deckY() { return Math.floorDiv(topHalf - 1, 2); }
		boolean slab() { return (topHalf & 1) != 0; }
		int roofY() { return deckY() + TUNNEL_HEIGHT; }
		double ridingY() { return topHalf / 2.0; }
	}

	static Map<Cell, Column> create(List<Cell> road, Map<Cell, Integer> surfaces, Set<Cell> liquid, Set<Cell> voids, double grade,
		Cell start) {
		var heights = TrackGeometry.halfSteps(surfaces, liquid, grade, start);
		Set<Cell> roadSet = new HashSet<>(road);
		Map<Cell, Column> plan = new LinkedHashMap<>();
		Set<Cell> roofLights = spaced(road);
		for (Cell cell : road) {
			int top = heights.get(cell);
			Kind kind = kind(top, surfaces.get(cell), liquid.contains(cell), voids.contains(cell));
			plan.put(cell, new Column(top, kind, false, roofLights.contains(cell), false));
		}
		Map<Cell, Integer> borders = new LinkedHashMap<>();
		Map<Cell, Kind> borderKinds = new HashMap<>();
		for (Cell cell : road) {
			Column lane = plan.get(cell);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				Cell edge = new Cell(cell.x() + dx, cell.z() + dz);
				if (roadSet.contains(edge)) continue;
				borders.merge(edge, lane.topHalf, Math::max);
				borderKinds.merge(edge, lane.kind, (a, b) -> a.ordinal() > b.ordinal() ? a : b);
			}
		}
		Set<Cell> lights = spaced(borders.keySet());
		borders.forEach((cell, top) -> {
			Kind kind = borderKinds.get(cell);
			// Never hollow out a wet shoulder for a bridge or bore a tunnel into liquid.
			if (liquid.contains(cell)) kind = Kind.GROUND;
			boolean light = lights.contains(cell);
			plan.put(cell, new Column(top, kind, true, light,
				kind == Kind.BRIDGE && light && !voids.contains(cell)));
		});
		return Collections.unmodifiableMap(plan);
	}

	private static Kind kind(int topHalf, int terrainY, boolean wet, boolean empty) {
		if (wet) return Kind.GROUND;
		if (empty || topHalf - 2 * (terrainY + 1) >= BRIDGE_FILL_HALVES) return Kind.BRIDGE;
		if (terrainY >= Math.floorDiv(topHalf - 1, 2) + TUNNEL_HEIGHT) return Kind.TUNNEL;
		return Kind.GROUND;
	}

	/** Greedy eight-block spacing with a spatial grid: linear work even for a 20 km track. */
	private static Set<Cell> spaced(Collection<Cell> cells) {
		Map<Cell, List<Cell>> grid = new HashMap<>();
		Set<Cell> lights = new HashSet<>();
		for (Cell cell : cells) {
			int gx = Math.floorDiv(cell.x(), 8), gz = Math.floorDiv(cell.z(), 8);
			boolean near = false;
			for (int dx = -1; dx <= 1 && !near; dx++) for (int dz = -1; dz <= 1 && !near; dz++) {
				for (Cell light : grid.getOrDefault(new Cell(gx + dx, gz + dz), List.of())) {
					int x = light.x() - cell.x(), z = light.z() - cell.z();
					if (x * x + z * z < 64) { near = true; break; }
				}
			}
			if (!near) {
				lights.add(cell);
				grid.computeIfAbsent(new Cell(gx, gz), ignored -> new ArrayList<>()).add(cell);
			}
		}
		return lights;
	}
}
