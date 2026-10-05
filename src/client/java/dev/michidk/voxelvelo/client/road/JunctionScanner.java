package dev.michidk.voxelvelo.client.road;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a junction on the road ahead.
 *
 * <p>The road in front of the bike is followed row by row across the heading. Where it suddenly gets much wider
 * (a cross street, a turn-off) a junction starts. Around its middle a ring of road samples is read: every run
 * of road on that ring is a way out, and the run the bike came from is the way in. A single way out that is
 * not straight ahead is a corner; two or more is a choice.
 *
 * <p>Pure logic over {@link RoadWorld}, like {@link RoadFollower}.
 */
public final class JunctionScanner {
	private static final int RING_SAMPLES = 72;
	/** Narrower runs on the ring are noise (a lone block, a stub), not a way out. */
	private static final double MIN_ARM_WIDTH = 2.0;
	/** An exit this far off the heading counts as left or right rather than straight on. */
	private static final double SIDE_ANGLE = Math.toRadians(12.0);
	/** A lone exit must turn at least this much to be a corner worth taking over steering for. */
	private static final double CORNER_ANGLE = Math.toRadians(60.0);
	/** Ways out that double back more than this are the road behind, not a choice. */
	private static final double MAX_EXIT_ANGLE = Math.toRadians(150.0);
	/** The bike must come from within this angle of an arm on the ring for that arm to be the way in. */
	private static final double ENTRY_TOLERANCE = Math.toRadians(40.0);
	/** How far past the edge of the road a second road may start and still count as a branch of it. */
	private static final double BRANCH_REACH = 10.0;

	private JunctionScanner() {
	}

	/**
	 * @param maxDistance how far ahead of the bike to look, in blocks
	 * @return the junction ahead, or null when the road simply carries on (or there is no road)
	 */
	public static Junction scan(RoadWorld world, double x, double y, double z, double yawDegrees, int maxHalfWidth, double maxDistance) {
		double yaw = Math.toRadians(yawDegrees);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;

		int groundY = RoadFollower.floor(y - 0.05);
		int rowY = RoadFollower.groundAlong(world, x, z, fx, fz, groundY, 2.0);
		RoadFollower.Section near = RoadFollower.section(world, x + fx * 2.0, z + fz * 2.0, rx, rz, rowY, maxHalfWidth);
		if (near == null || near.tooWide()) {
			return null;
		}
		double baseWidth = near.high() - near.low();
		double limit = Math.max(baseWidth * 1.6, baseWidth + 3.0);

		double lateral = near.centre();
		for (double d = 3.0; d <= maxDistance; d += 1.0) {
			double qx = x + fx * d + rx * lateral;
			double qz = z + fz * d + rz * lateral;
			RoadFollower.Section row = RoadFollower.section(world, qx, qz, rx, rz, rowY, maxHalfWidth);
			if (row == null) {
				return null;
			}
			if (isMouth(world, qx, qz, rx, rz, rowY, row, limit, maxHalfWidth)) {
				// The rows are a block apart, so the mouth began somewhere in the last block: find where.
				double begin = d;
				double before = d - 1.0;
				for (int i = 0; i < 5; i++) {
					double mid = (begin + before) / 2.0;
					double mx = x + fx * mid + rx * lateral;
					double mz = z + fz * mid + rz * lateral;
					RoadFollower.Section at = RoadFollower.section(world, mx, mz, rx, rz, rowY, maxHalfWidth);
					if (at != null && isMouth(world, mx, mz, rx, rz, rowY, at, limit, maxHalfWidth)) {
						begin = mid;
					} else {
						before = mid;
					}
				}
				return ring(world, x, z, fx, fz, rx, rz, begin + baseWidth / 2.0, d - 1.0, lateral, rowY, baseWidth);
			}
			lateral += row.centre();
			int column = RoadFollower.columnY(world, RoadFollower.floor(qx + rx * row.centre()), RoadFollower.floor(qz + rz * row.centre()), rowY);
			if (column != RoadFollower.NONE) {
				rowY = column;
			}
		}
		return null;
	}

	private static Junction ring(RoadWorld world, double x, double z, double fx, double fz, double rx, double rz,
		double forward, double seedForward, double lateral, int refY, double baseWidth) {
		double cx = x + fx * forward + rx * lateral;
		double cz = z + fz * forward + rz * lateral;
		double radius = Math.max(6.0, baseWidth / 2.0 + 5.0);
		double step = 2.0 * Math.PI / RING_SAMPLES;

		Map<Long, Integer> reached = reachable(world, RoadFollower.floor(x + fx * seedForward + rx * lateral),
			RoadFollower.floor(z + fz * seedForward + rz * lateral), refY, cx, cz, radius + 1.0);
		if (reached == null) {
			return null;
		}
		boolean[] on = new boolean[RING_SAMPLES];
		int count = 0;
		for (int i = 0; i < RING_SAMPLES; i++) {
			double theta = angle(i, step);
			double px = cx + radius * (Math.cos(theta) * fx + Math.sin(theta) * rx);
			double pz = cz + radius * (Math.cos(theta) * fz + Math.sin(theta) * rz);
			on[i] = reached.containsKey(key(RoadFollower.floor(px), RoadFollower.floor(pz)));
			if (on[i]) {
				count++;
			}
		}
		if (count == 0 || count == RING_SAMPLES) {
			return null;
		}

		List<double[]> arms = new ArrayList<>();
		int first = 0;
		while (on[first] && on[(first - 1 + RING_SAMPLES) % RING_SAMPLES]) {
			first++;
		}
		for (int n = 0; n < RING_SAMPLES; n++) {
			int i = (first + n) % RING_SAMPLES;
			if (!on[i] || on[(i - 1 + RING_SAMPLES) % RING_SAMPLES]) {
				continue;
			}
			int length = 0;
			while (on[(i + length) % RING_SAMPLES]) {
				length++;
			}
			double width = length * step * radius;
			if (width >= MIN_ARM_WIDTH) {
				arms.add(new double[] { wrap(angle(i, step) + (length - 1) * step / 2.0), width });
			}
		}

		double toBike = Math.atan2((x - cx) * rx + (z - cz) * rz, (x - cx) * fx + (z - cz) * fz);
		double[] entry = null;
		for (double[] arm : arms) {
			if (Math.abs(wrap(arm[0] - toBike)) <= ENTRY_TOLERANCE && (entry == null || Math.abs(wrap(arm[0] - toBike)) < Math.abs(wrap(entry[0] - toBike)))) {
				entry = arm;
			}
		}
		if (entry == null) {
			return null;
		}

		List<double[]> ways = new ArrayList<>();
		for (double[] arm : arms) {
			if (arm != entry && Math.abs(arm[0]) <= MAX_EXIT_ANGLE) {
				ways.add(arm);
			}
		}
		if (ways.isEmpty() || ways.size() == 1 && Math.abs(ways.get(0)[0]) < CORNER_ANGLE) {
			return null;
		}

		double[] leftmost = null;
		double[] rightmost = null;
		for (double[] way : ways) {
			if (way[0] < -SIDE_ANGLE && (leftmost == null || way[0] < leftmost[0])) {
				leftmost = way;
			}
			if (way[0] > SIDE_ANGLE && (rightmost == null || way[0] > rightmost[0])) {
				rightmost = way;
			}
		}
		List<Junction.Exit> exits = new ArrayList<>();
		for (double[] way : ways) {
			Junction.Side side = way == leftmost ? Junction.Side.LEFT : way == rightmost ? Junction.Side.RIGHT : Junction.Side.STRAIGHT;
			double dx = Math.cos(way[0]) * fx + Math.sin(way[0]) * rx;
			double dz = Math.cos(way[0]) * fz + Math.sin(way[0]) * rz;
			exits.add(new Junction.Exit(side, Math.toDegrees(way[0]), way[1], dx, dz));
		}
		return new Junction(cx, cz, refY, radius, fx, fz, List.copyOf(exits));
	}

	/** Angle of ring sample i, from -pi (behind) through 0 (ahead) to +pi; positive is to the right. */
	private static double angle(int i, double step) {
		return -Math.PI + i * step;
	}

	private static double wrap(double angle) {
		while (angle > Math.PI) {
			angle -= 2.0 * Math.PI;
		}
		while (angle <= -Math.PI) {
			angle += 2.0 * Math.PI;
		}
		return angle;
	}

	/** Whether the road across this row has suddenly widened, run into a cross street, or forks. */
	private static boolean isMouth(RoadWorld world, double qx, double qz, double rx, double rz, int refY, RoadFollower.Section row, double limit, int maxHalfWidth) {
		return row.tooWide() || row.high() - row.low() > limit || roadBeside(world, qx, qz, rx, rz, refY, row);
	}

	/** Whether another road starts a little way past either edge of this run, as at a fork. */
	private static boolean roadBeside(RoadWorld world, double qx, double qz, double rx, double rz, int refY, RoadFollower.Section row) {
		for (double s = 0.5; s <= BRANCH_REACH; s += 0.5) {
			if (RoadFollower.columnY(world, RoadFollower.floor(qx + rx * (row.high() + s)), RoadFollower.floor(qz + rz * (row.high() + s)), refY) != RoadFollower.NONE
				|| RoadFollower.columnY(world, RoadFollower.floor(qx + rx * (row.low() - s)), RoadFollower.floor(qz + rz * (row.low() - s)), refY) != RoadFollower.NONE) {
				return true;
			}
		}
		return false;
	}

	/** Every road column connected to the seed within reach of the junction middle; keyed by column. Null if the seed is not road. */
	private static Map<Long, Integer> reachable(RoadWorld world, int sx, int sz, int sy, double cx, double cz, double reach) {
		int seedY = RoadFollower.columnY(world, sx, sz, sy);
		if (seedY == RoadFollower.NONE) {
			return null;
		}
		Map<Long, Integer> seen = new HashMap<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		seen.put(key(sx, sz), seedY);
		queue.add(new int[] { sx, sz, seedY });
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nx = at[0] + dx;
					int nz = at[1] + dz;
					if ((dx == 0 && dz == 0) || seen.containsKey(key(nx, nz)) || Math.hypot(nx + 0.5 - cx, nz + 0.5 - cz) > reach) {
						continue;
					}
					int ny = RoadFollower.columnY(world, nx, nz, at[2]);
					if (ny != RoadFollower.NONE) {
						seen.put(key(nx, nz), ny);
						queue.add(new int[] { nx, nz, ny });
					}
				}
			}
		}
		return seen;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}
}
