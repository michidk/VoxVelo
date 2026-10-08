package dev.michidk.voxvelo.fitness.client.road;

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
	/** The road's own width is measured this far ahead of the bike. */
	private static final double NEAR_ROW = 2.0;
	/** The first row checked for a junction, and the distance between rows. */
	private static final double FIRST_ROW = 3.0;
	private static final double ROW_STEP = 1.0;
	/** A row is a junction's mouth when it is this much wider than the road: 60 % or three blocks, whichever is more. */
	private static final double MOUTH_WIDTH_FACTOR = 1.6;
	private static final double MOUTH_WIDTH_MARGIN = 3.0;
	/** Halvings of the last row step to find where the mouth begins (a sixteenth of a block apart). */
	private static final int MOUTH_BISECTIONS = 5;
	private static final int RING_SAMPLES = 72;
	/** The ring runs this far past the road's edge, and never closer than {@link #MIN_RING_RADIUS} to the middle. */
	private static final double RING_MARGIN = 5.0;
	private static final double MIN_RING_RADIUS = 6.0;
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
	private static final double BRANCH_STEP = 0.5;

	/**
	 * A run of road on the ring.
	 *
	 * @param angle radians from the heading to the middle of the run, positive to the right
	 * @param width length of the run along the ring, in blocks
	 */
	private record Arm(double angle, double width) {
	}

	private JunctionScanner() {
	}

	/**
	 * @param maxDistance how far ahead of the bike to look, in blocks
	 * @return the junction ahead, or null when the road simply carries on (or there is no road)
	 */
	public static Junction scan(RoadWorld world, double x, double y, double z, double yawDegrees, int maxHalfWidth, double maxDistance) {
		Heading heading = Heading.ofYaw(yawDegrees);
		double fx = heading.fx();
		double fz = heading.fz();
		double rx = heading.rx();
		double rz = heading.rz();

		int groundY = RoadFollower.groundY(y);
		int rowY = RoadFollower.groundAlong(world, x, z, fx, fz, groundY, NEAR_ROW);
		RoadFollower.Section near = RoadFollower.section(world, x + fx * NEAR_ROW, z + fz * NEAR_ROW, rx, rz, rowY, maxHalfWidth);
		if (near == null || near.tooWide()) {
			return null;
		}
		double baseWidth = near.high() - near.low();
		double limit = Math.max(baseWidth * MOUTH_WIDTH_FACTOR, baseWidth + MOUTH_WIDTH_MARGIN);

		double lateral = near.centre();
		for (double d = FIRST_ROW; d <= maxDistance; d += ROW_STEP) {
			double qx = x + fx * d + rx * lateral;
			double qz = z + fz * d + rz * lateral;
			RoadFollower.Section row = RoadFollower.section(world, qx, qz, rx, rz, rowY, maxHalfWidth);
			if (row == null) {
				return null;
			}
			if (isMouth(world, qx, qz, rx, rz, rowY, row, limit, maxHalfWidth)) {
				// The rows are a step apart, so the mouth began somewhere in the last step: find where.
				double begin = d;
				double before = d - ROW_STEP;
				for (int i = 0; i < MOUTH_BISECTIONS; i++) {
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
				return ring(world, x, z, heading, begin + baseWidth / 2.0, d - ROW_STEP, lateral, rowY, baseWidth);
			}
			lateral += row.centre();
			int column = RoadFollower.columnY(world, RoadFollower.floor(qx + rx * row.centre()), RoadFollower.floor(qz + rz * row.centre()), rowY);
			if (column != RoadFollower.NONE) {
				rowY = column;
			}
		}
		return null;
	}

	/** Reads the ring around the junction middle and turns the road runs on it into the way in and the ways out. */
	private static Junction ring(RoadWorld world, double x, double z, Heading heading,
		double forward, double seedForward, double lateral, int refY, double baseWidth) {
		double fx = heading.fx();
		double fz = heading.fz();
		double rx = heading.rx();
		double rz = heading.rz();
		double cx = x + fx * forward + rx * lateral;
		double cz = z + fz * forward + rz * lateral;
		double radius = Math.max(MIN_RING_RADIUS, baseWidth / 2.0 + RING_MARGIN);

		Map<Long, Integer> reached = reachable(world, RoadFollower.floor(x + fx * seedForward + rx * lateral),
			RoadFollower.floor(z + fz * seedForward + rz * lateral), refY, cx, cz, radius + 1.0);
		if (reached == null) {
			return null;
		}
		boolean[] on = sampleRing(reached, cx, cz, radius, heading);
		if (on == null) {
			return null;
		}
		List<Arm> arms = findArms(on, radius);
		double toBike = Math.atan2((x - cx) * rx + (z - cz) * rz, (x - cx) * fx + (z - cz) * fz);
		Arm entry = pickEntry(arms, toBike);
		if (entry == null) {
			return null;
		}
		List<Junction.Exit> exits = classifyExits(arms, entry, heading);
		return exits == null ? null : new Junction(cx, cz, refY, radius, fx, fz, exits);
	}

	/** Which ring samples are connected road; null when none or all are, so there are no separate ways. */
	private static boolean[] sampleRing(Map<Long, Integer> reached, double cx, double cz, double radius, Heading heading) {
		double step = 2.0 * Math.PI / RING_SAMPLES;
		boolean[] on = new boolean[RING_SAMPLES];
		int count = 0;
		for (int i = 0; i < RING_SAMPLES; i++) {
			double theta = angle(i, step);
			double px = cx + radius * (Math.cos(theta) * heading.fx() + Math.sin(theta) * heading.rx());
			double pz = cz + radius * (Math.cos(theta) * heading.fz() + Math.sin(theta) * heading.rz());
			on[i] = reached.containsKey(key(RoadFollower.floor(px), RoadFollower.floor(pz)));
			if (on[i]) {
				count++;
			}
		}
		return count == 0 || count == RING_SAMPLES ? null : on;
	}

	/** Every run of road on the ring at least {@link #MIN_ARM_WIDTH} wide, in ring order starting after a gap. */
	private static List<Arm> findArms(boolean[] on, double radius) {
		double step = 2.0 * Math.PI / RING_SAMPLES;
		List<Arm> arms = new ArrayList<>();
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
				arms.add(new Arm(wrap(angle(i, step) + (length - 1) * step / 2.0), width));
			}
		}
		return arms;
	}

	/** The arm closest to the direction the bike comes from, if it is within {@link #ENTRY_TOLERANCE}. */
	private static Arm pickEntry(List<Arm> arms, double toBike) {
		Arm entry = null;
		for (Arm arm : arms) {
			if (Math.abs(wrap(arm.angle() - toBike)) <= ENTRY_TOLERANCE
				&& (entry == null || Math.abs(wrap(arm.angle() - toBike)) < Math.abs(wrap(entry.angle() - toBike)))) {
				entry = arm;
			}
		}
		return entry;
	}

	/**
	 * The ways on besides the entry, the leftmost and rightmost of them marked as the sides. Null when there is no
	 * choice to make: no way on, or a single one that hardly turns.
	 */
	private static List<Junction.Exit> classifyExits(List<Arm> arms, Arm entry, Heading heading) {
		List<Arm> ways = new ArrayList<>();
		for (Arm arm : arms) {
			if (arm != entry && Math.abs(arm.angle()) <= MAX_EXIT_ANGLE) {
				ways.add(arm);
			}
		}
		if (ways.isEmpty() || ways.size() == 1 && Math.abs(ways.get(0).angle()) < CORNER_ANGLE) {
			return null;
		}

		Arm leftmost = null;
		Arm rightmost = null;
		for (Arm way : ways) {
			if (way.angle() < -SIDE_ANGLE && (leftmost == null || way.angle() < leftmost.angle())) {
				leftmost = way;
			}
			if (way.angle() > SIDE_ANGLE && (rightmost == null || way.angle() > rightmost.angle())) {
				rightmost = way;
			}
		}
		List<Junction.Exit> exits = new ArrayList<>();
		for (Arm way : ways) {
			Junction.Side side = way == leftmost ? Junction.Side.LEFT : way == rightmost ? Junction.Side.RIGHT : Junction.Side.STRAIGHT;
			double dx = Math.cos(way.angle()) * heading.fx() + Math.sin(way.angle()) * heading.rx();
			double dz = Math.cos(way.angle()) * heading.fz() + Math.sin(way.angle()) * heading.rz();
			exits.add(new Junction.Exit(side, Math.toDegrees(way.angle()), way.width(), dx, dz));
		}
		return List.copyOf(exits);
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
		for (double s = BRANCH_STEP; s <= BRANCH_REACH; s += BRANCH_STEP) {
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
