package dev.michidk.voxvelo.client.road;

import dev.michidk.voxvelo.bike.BikePhysics;

/**
 * Steers a bike along the centre of a road.
 *
 * <p>A <em>road column</em> is a block column with a road block whose two blocks above are passable (air). The
 * road across some point is the contiguous run of road columns through it, measured perpendicular to the
 * heading. The follower picks a point ahead of the bike, finds that run, takes its middle, and steers toward it
 * with a pure-pursuit rule, so the bike converges onto the centre line and follows bends.
 *
 * <p>Pure logic over {@link RoadWorld}; all positions are in blocks and yaw is Minecraft yaw in degrees.
 */
public final class RoadFollower {
	/** Sampling step across the road, in blocks. */
	private static final double STEP = 0.5;
	/** How far sideways from the bike to look for a road when it is not exactly on one. */
	private static final double SEARCH_SIDEWAYS = 3.0;
	static final int NONE = Integer.MIN_VALUE;
	/** How much longer than its real width a road may measure across a bike heading at a slant. */
	private static final double WIDTH_TOLERANCE = 1.5;

	/**
	 * @param hasTarget whether a road centre was found ahead
	 * @param onRoad    whether the bike is currently on a road column
	 * @param steering  -1 (left) .. +1 (right); zero when there is no target
	 * @param targetX   world x of the aim point (valid when hasTarget)
	 * @param targetZ   world z of the aim point (valid when hasTarget)
	 */
	public record Result(boolean hasTarget, boolean onRoad, double steering, double targetX, double targetZ) {
		static Result none(boolean onRoad) {
			return new Result(false, onRoad, 0.0, 0.0, 0.0);
		}
	}

	record Section(double low, double high, boolean tooWide) {
		double centre() {
			return (this.low + this.high) / 2.0;
		}
	}

	private RoadFollower() {
	}

	/**
	 * @param speed        forward speed in m/s; a faster bike looks further ahead
	 * @param params       physics of the bike, for its wheelbase and steering lock
	 * @param maxHalfWidth widest road, in blocks each side of the aim point; wider is treated as open ground
	 */
	public static Result steer(RoadWorld world, double x, double y, double z, double yawDegrees, double speed,
		BikePhysics.Params params, int maxHalfWidth) {
		Heading heading = Heading.ofYaw(yawDegrees);
		double fx = heading.fx();
		double fz = heading.fz();
		double rx = heading.rx();
		double rz = heading.rz();

		int groundY = groundY(y);
		boolean onRoad = columnY(world, floor(x), floor(z), groundY) != NONE;

		double lookahead = clamp(2.5 + 0.9 * speed, 3.0, 9.0);
		for (double d : new double[] { lookahead, lookahead * 0.75, Math.max(2.0, lookahead * 0.5) }) {
			int refY = groundAlong(world, x, z, fx, fz, groundY, d);
			double qx = x + fx * d;
			double qz = z + fz * d;
			Section section = section(world, qx, qz, rx, rz, refY, maxHalfWidth);
			if (section != null && !section.tooWide()) {
				double tx = qx + rx * section.centre();
				double tz = qz + rz * section.centre();
				return new Result(true, onRoad, pursuit(tx - x, tz - z, fx, fz, rx, rz, speed, params), tx, tz);
			}
		}
		return Result.none(onRoad);
	}

	/** Steering that arcs the bike from its pose through the given world point, as -1..+1. */
	public static double steerToward(double x, double z, double yawDegrees, double speed, BikePhysics.Params params, double targetX, double targetZ) {
		Heading heading = Heading.ofYaw(yawDegrees);
		return pursuit(targetX - x, targetZ - z, heading.fx(), heading.fz(), heading.rx(), heading.rz(), speed, params);
	}

	/** Pure pursuit: the front wheel angle that would arc the bike through the aim point, as -1..+1. */
	static double pursuit(double vx, double vz, double fx, double fz, double rx, double rz, double speed, BikePhysics.Params p) {
		double forward = vx * fx + vz * fz;
		double right = vx * rx + vz * rz;
		double distance = Math.hypot(forward, right);
		if (distance < 1.0e-6) {
			return 0.0;
		}
		double alpha = Math.atan2(right, forward);
		double wheelAngle = Math.atan2(2.0 * p.wheelBase() * Math.sin(alpha), distance);
		return clamp(wheelAngle / BikePhysics.maxSteerAngle(speed, p), -1.0, 1.0);
	}

	/** The road run across the line through (qx, qz) along (rx, rz), or null if there is no road near the line. */
	static Section section(RoadWorld world, double qx, double qz, double rx, double rz, int refY, int maxHalfWidth) {
		double start = Double.NaN;
		int startY = NONE;
		search:
		for (double s = 0.0; s <= SEARCH_SIDEWAYS; s += STEP) {
			for (int sign = 1; sign >= -1; sign -= 2) {
				if (s == 0.0 && sign < 0) {
					continue;
				}
				double offset = s * sign;
				int col = columnY(world, floor(qx + rx * offset), floor(qz + rz * offset), refY);
				if (col != NONE) {
					start = offset;
					startY = col;
					break search;
				}
			}
		}
		if (Double.isNaN(start)) {
			return null;
		}

		// A run is measured across the heading, so a bike that is not parallel to the road sees it longer than it
		// is wide. Allow for that, and give up on anything past the cap (an open plaza or a cross street).
		double cap = maxHalfWidth * WIDTH_TOLERANCE;
		double high = start;
		int y = startY;
		boolean tooWide = false;
		boolean refineHigh = false;
		boolean refineLow = false;
		for (double s = start + STEP; ; s += STEP) {
			if (s - start > 2.0 * cap) {
				tooWide = true;
				break;
			}
			int col = columnY(world, floor(qx + rx * s), floor(qz + rz * s), y);
			if (col == NONE) {
				refineHigh = true;
				break;
			}
			high = s;
			y = col;
		}
		double low = start;
		y = startY;
		for (double s = start - STEP; ; s -= STEP) {
			if (start - s > 2.0 * cap) {
				tooWide = true;
				break;
			}
			int col = columnY(world, floor(qx + rx * s), floor(qz + rz * s), y);
			if (col == NONE) {
				refineLow = true;
				break;
			}
			low = s;
			y = col;
		}
		if (!tooWide) {
			// The half-block sampling grid would bias the centre by up to a quarter block; find the true edges.
			if (refineHigh) {
				high = edge(world, qx, qz, rx, rz, y, high, high + STEP);
			}
			if (refineLow) {
				low = edge(world, qx, qz, rx, rz, y, low, low - STEP);
			}
		}
		return new Section(low, high, tooWide || high - low > 2.0 * cap);
	}

	/** Bisects between a road sample and the first non-road sample after it to locate the road edge. */
	private static double edge(RoadWorld world, double qx, double qz, double rx, double rz, int refY, double inside, double outside) {
		for (int i = 0; i < 6; i++) {
			double mid = (inside + outside) / 2.0;
			if (columnY(world, floor(qx + rx * mid), floor(qz + rz * mid), refY) != NONE) {
				inside = mid;
			} else {
				outside = mid;
			}
		}
		return (inside + outside) / 2.0;
	}

	/** Follows the ground height along the heading so a climbing or falling road stays within one block per column. */
	static int groundAlong(RoadWorld world, double x, double z, double fx, double fz, int startY, double distance) {
		int y = startY;
		for (double t = STEP; t <= distance; t += STEP) {
			int col = columnY(world, floor(x + fx * t), floor(z + fz * t), y);
			if (col != NONE) {
				y = col;
			}
		}
		return y;
	}

	/** Height of the road block in this column near refY, with two blocks of passable space above it, or NONE. */
	static int columnY(RoadWorld world, int x, int z, int refY) {
		for (int dy : new int[] { 0, 1, -1 }) {
			int y = refY + dy;
			if (world.isRoad(x, y, z) && world.isPassable(x, y + 1, z) && world.isPassable(x, y + 2, z)) {
				return y;
			}
		}
		return NONE;
	}

	/** The block a bike at this height stands on; a bike resting exactly on a block's top counts that block. */
	static int groundY(double y) {
		return floor(y - 0.05);
	}

	static int floor(double value) {
		return (int) Math.floor(value);
	}

	static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
