package dev.michidk.voxvelo.bikes.track;

import java.util.*;
import java.util.function.Function;

/** Incremental tree discovery. Leaves lead to one trunk, never from one canopy into another trunk. */
final class TrackTreePlan {
	record Pos(int x, int y, int z) {
		Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
	}
	record Node(String log, int leafDistance) {
		static final Node OTHER = new Node(null, 0);
		boolean leaf() { return leafDistance > 0; }
	}
	private record Visit(Pos pos, int distance) {}
	private enum Phase { FIND, LOGS, LEAVES, DONE }
	private final Pos origin;
	private final Function<Pos, Node> read;
	private final Deque<Visit> queue = new ArrayDeque<>();
	private final Set<Pos> seen = new HashSet<>();
	private final Set<Pos> logs = new LinkedHashSet<>();
	private final Set<Pos> leaves = new LinkedHashSet<>();
	private Phase phase = Phase.FIND;
	private String species;

	TrackTreePlan(Pos origin, Function<Pos, Node> read) {
		this.origin = origin;
		this.read = read;
		queue.add(new Visit(origin, 0));
	}

	/** One bounded expansion. A temporarily unavailable read may throw; retrying is safe. */
	boolean step() {
		if (phase == Phase.DONE) return true;
		if (queue.isEmpty()) {
			if (phase == Phase.LOGS) {
				phase = Phase.LEAVES;
				seen.clear();
				for (Pos log : logs) queue.add(new Visit(log, 0));
			} else phase = Phase.DONE;
			return phase == Phase.DONE;
		}
		Visit visit = queue.getFirst();
		Pos pos = visit.pos();
		if (seen.contains(pos)) { queue.removeFirst(); return false; }
		Node node = read.apply(pos);
		List<Visit> next = new ArrayList<>();
		boolean foundLog = phase == Phase.FIND && node.log() != null;
		if (foundLog) {
			species = node.log();
			queue.clear();
			seen.clear();
			phase = Phase.LOGS;
			queue.add(new Visit(pos, 0));
			return false;
		}
		if (phase == Phase.LOGS && species.equals(node.log())) {
			// Branches can meet diagonally (acacia, cherry and large oaks).
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dy == 0 && dz == 0) continue;
				Pos neighbor = pos.offset(dx, dy, dz);
				if (species.equals(read.apply(neighbor).log())) {
					checkBounds(neighbor);
					next.add(new Visit(neighbor, 0));
				}
			}
		} else if ((phase == Phase.FIND && node.leaf()) || phase == Phase.LEAVES) {
			for (int[] d : DIRECTIONS) {
				Pos neighbor = pos.offset(d[0], d[1], d[2]);
				Node other = read.apply(neighbor);
				if (phase == Phase.FIND) {
					if (other.log() != null || (other.leaf() && other.leafDistance() < node.leafDistance()))
						next.add(new Visit(neighbor, 0));
				} else if (visit.distance() < 6 && other.leafDistance() == visit.distance() + 1) {
					checkBounds(neighbor);
					next.add(new Visit(neighbor, visit.distance() + 1));
				}
			}
		}
		// Commit only after all reads succeeded; pending chunk loads cannot leave a half-expanded node.
		queue.removeFirst();
		seen.add(pos);
		if (phase == Phase.LOGS && species.equals(node.log())) logs.add(pos);
		if (phase == Phase.LEAVES && node.leaf()) leaves.add(pos);
		queue.addAll(next);
		if (seen.size() > 32768) throw new IllegalStateException("Tree cleanup exceeds 32768 blocks");
		return false;
	}

	Set<Pos> removal() {
		if (phase != Phase.DONE) throw new IllegalStateException("Tree discovery still running");
		// A bare log structure without a natural canopy is not treated as a tree.
		Set<Pos> result = new LinkedHashSet<>();
		if (!leaves.isEmpty()) { result.addAll(leaves); result.addAll(logs); }
		return result;
	}

	private void checkBounds(Pos pos) {
		if (Math.abs(pos.x() - origin.x()) > 32 || Math.abs(pos.z() - origin.z()) > 32
			|| Math.abs(pos.y() - origin.y()) > 96)
			throw new IllegalStateException("Tree cleanup extends beyond its safe search bounds");
	}

	private static final int[][] DIRECTIONS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
}
