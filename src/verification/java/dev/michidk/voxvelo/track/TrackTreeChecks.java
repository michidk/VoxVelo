package dev.michidk.voxvelo.track;

import java.util.*;

/** Tree identity, canopy ownership and resumable discovery without a Minecraft world. */
final class TrackTreeChecks {
	private static TrackTreePlan.Pos p(int x, int y, int z) { return new TrackTreePlan.Pos(x, y, z); }
	private static final TrackTreePlan.Node LOG = new TrackTreePlan.Node("birch", 0);

	static void run() {
		Map<TrackTreePlan.Pos, TrackTreePlan.Node> world = new HashMap<>();
		for (int y = 0; y <= 12; y++) world.put(p(15, y, 0), LOG);
		// Long crown crossing x=16, outside both the road and the original chunk.
		for (int x = 16; x <= 21; x++) world.put(p(x, 12, 0), new TrackTreePlan.Node(null, x - 15));
		Set<TrackTreePlan.Pos> whole = discover(world, p(15, 2, 0), false);
		require(whole.equals(world.keySet()), "cut trunk removes full height and out-of-chunk canopy");
		require(discover(world, p(20, 12, 0), false).equals(whole), "clipped canopy finds its trunk");
		require(discover(world, p(20, 12, 0), true).equals(whole), "pending reads resume without losing branches");

		// A touching crown belongs to a surviving tree when it is closer to that trunk.
		world.put(p(22, 12, 0), LOG);
		world.put(p(21, 12, 0), new TrackTreePlan.Node(null, 1));
		world.put(p(20, 12, 0), new TrackTreePlan.Node(null, 2));
		world.put(p(19, 12, 0), new TrackTreePlan.Node(null, 3));
		Set<TrackTreePlan.Pos> cut = discover(world, p(15, 2, 0), false);
		require(!cut.contains(p(22, 12, 0)) && !cut.contains(p(21, 12, 0)) && !cut.contains(p(19, 12, 0)),
			"touching foliage cannot recruit a neighboring trunk or its closer leaves");
		require(cut.contains(p(18, 12, 0)), "original tree's canopy is still removed");

		Map<TrackTreePlan.Pos, TrackTreePlan.Node> branch = new HashMap<>();
		branch.put(p(0, 0, 0), LOG);
		branch.put(p(1, 1, 1), LOG);
		branch.put(p(2, 1, 1), new TrackTreePlan.Node(null, 1));
		require(discover(branch, p(0, 0, 0), false).size() == 3, "diagonal branches are part of the tree");
		branch.put(p(0, 1, 0), new TrackTreePlan.Node("oak", 0));
		require(!discover(branch, p(0, 0, 0), false).contains(p(0, 1, 0)), "different log species are not recruited");
		require(discover(Map.of(p(0, 0, 0), LOG), p(0, 0, 0), false).isEmpty(),
			"bare timber without natural leaves is left alone");
		require(discover(Map.of(), p(0, 0, 0), false).isEmpty(), "persistent leaves mapped to OTHER are ignored");

		Map<TrackTreePlan.Pos, TrackTreePlan.Node> oversized = new HashMap<>();
		for (int x = 0; x < 40; x++) oversized.put(p(x, 0, 0), LOG);
		boolean rejected = false;
		try { discover(oversized, p(0, 0, 0), false); }
		catch (IllegalStateException expected) { rejected = true; }
		require(rejected, "oversized connected timber aborts discovery before any removal");
		System.out.println("Whole-tree cleanup, neighboring canopies and resumable discovery passed.");
	}

	private static Set<TrackTreePlan.Pos> discover(Map<TrackTreePlan.Pos, TrackTreePlan.Node> world,
												TrackTreePlan.Pos seed, boolean interrupt) {
		Set<TrackTreePlan.Pos> ready = new HashSet<>();
		class Pending extends RuntimeException {}
		TrackTreePlan plan = new TrackTreePlan(seed, pos -> {
			if (interrupt && ready.add(pos)) throw new Pending();
			return world.getOrDefault(pos, TrackTreePlan.Node.OTHER);
		});
		for (int i = 0; i < 100000; i++) {
			try { if (plan.step()) return plan.removal(); }
			catch (Pending pending) { /* The next tick can read this position. */ }
		}
		throw new AssertionError("discovery did not finish");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
