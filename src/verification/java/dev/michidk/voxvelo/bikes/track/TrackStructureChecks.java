package dev.michidk.voxvelo.bikes.track;

import dev.michidk.voxvelo.bikes.track.TrackGeometry.Cell;
import java.util.*;

/** Structure rules and half-block continuity, independent of Minecraft and chunk loading. */
final class TrackStructureChecks {
	static void run() {
		List<Cell> road = new ArrayList<>();
		Map<Cell, Integer> terrain = new LinkedHashMap<>();
		for (int x = -81; x <= 81; x++) for (int z = -1; z <= 5; z++) {
			Cell cell = new Cell(x, z);
			terrain.put(cell, 70 + (int) Math.round(35 * Math.sin(x / 12.0)));
			if (x >= -80 && x <= 80 && z >= 0 && z <= 4) road.add(cell);
		}
		Cell start = new Cell(0, 2);
		Set<Cell> lane = new HashSet<>(road);
		for (double grade : new double[] {.01, .08, .15}) {
			var plan = TrackPlan.create(road, terrain, Set.of(), Set.of(), grade, start);
			require(plan.keySet().equals(terrain.keySet()), "road and complete shoulder only");
			require(plan.get(start).ridingY() == 71, "start is anchored to terrain top");
			require(plan.values().stream().anyMatch(c -> c.kind() == TrackPlan.Kind.BRIDGE), "valleys get bridges");
			require(plan.values().stream().anyMatch(c -> c.kind() == TrackPlan.Kind.TUNNEL), "mountains get tunnels");
			// The 1% profile rounds completely flat on this symmetric fixture.
			if (grade >= .08) require(plan.values().stream().anyMatch(c -> !c.border() && c.slab()),
				"non-flat slopes include actual half steps");
			require(plan.values().stream().anyMatch(TrackPlan.Column::pier), "dry bridges have occasional edge piers");
			continuous(road, plan);
			for (var entry : plan.entrySet()) {
				Cell cell = entry.getKey();
				var section = entry.getValue();
				require(section.border() == !lane.contains(cell), "borders never narrow the riding lane");
				require(section.roofY() - section.ridingY() >= 5, "tunnel headroom includes slab height");
				if (section.border()) {
					require(plan.entrySet().stream().anyMatch(e -> e.getValue().border() && e.getValue().light()
						&& Math.hypot(e.getKey().x() - cell.x(), e.getKey().z() - cell.z()) < 8),
						"border lighting covers the circuit");
				}
			}
		}
		terrain.replaceAll((cell, y) -> 62);
		var wet = new HashSet<>(terrain.keySet());
		var water = TrackPlan.create(road, terrain, wet, Set.of(), .08, start);
		for (Cell cell : road) {
			require(water.get(cell).ridingY() == 63 && !water.get(cell).slab(), "flat water crossing stays above water");
			require(water.get(cell).kind() == TrackPlan.Kind.GROUND, "no underwater bridges or tunnels");
		}
		// A high lake's lower bound must spread across dry shore cells, not stop at the wet/dry seam.
		terrain.replaceAll((cell, y) -> cell.x() >= 40 ? 90 : 62);
		wet.removeIf(cell -> cell.x() < 40);
		water = TrackPlan.create(road, terrain, wet, Set.of(), .15, start);
		continuous(road, water);
		for (Cell cell : road) if (wet.contains(cell))
			require(water.get(cell).ridingY() >= 91, "lake never overtops the riding surface");

		terrain.replaceAll((cell, y) -> 79);
		var empty = TrackPlan.create(road, terrain, Set.of(), terrain.keySet(), .08, start);
		require(empty.values().stream().allMatch(c -> c.kind() == TrackPlan.Kind.BRIDGE && !c.pier()),
			"void spans have decks without pillars down to the world floor");
		var belowZero = new TrackPlan.Column(-1, TrackPlan.Kind.GROUND, false, false, false);
		require(belowZero.deckY() == -1 && belowZero.slab() && belowZero.ridingY() == -.5,
			"half-block positioning works below Y zero");
		System.out.println("Track structures, lighting, water approaches and half-block slopes passed.");
	}

	private static void continuous(List<Cell> road, Map<Cell, TrackPlan.Column> plan) {
		for (Cell cell : road) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			var next = plan.get(new Cell(cell.x() + dx, cell.z() + dz));
			if (next != null && !next.border())
				require(Math.abs(plan.get(cell).topHalf() - next.topHalf()) <= 1,
					"every riding neighbor, including diagonals, differs by at most half a block");
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
