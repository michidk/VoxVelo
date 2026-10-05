package dev.michidk.voxelvelo.client.road;

import dev.michidk.voxelvelo.bike.BikePhysics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides which way the bike takes at a junction while it follows a road.
 *
 * <p>A junction is picked up well ahead. If the cooldown allows, the rider is asked to choose left or right and
 * can answer until the bike reaches the turn; otherwise, or when no answer comes, the widest way on is taken
 * (a random one among equally wide ways). A lone corner is just taken. Turning steers through the middle of the
 * junction and out along the chosen way.
 *
 * <p>Pure logic, one {@link #update} call per game tick.
 */
public final class JunctionNavigator {
	public enum Phase {
		IDLE, PENDING, PROMPTING, TURNING
	}

	/**
	 * What the rider is being asked.
	 *
	 * @param left        whether a left turn exists
	 * @param right       whether a right turn exists
	 * @param chosen      the side the rider picked, or null
	 * @param defaultSide the way taken when the rider does not choose
	 * @param remaining   1 when the question appeared, falling to 0 when the turn starts
	 */
	public record Prompt(boolean left, boolean right, Junction.Side chosen, Junction.Side defaultSide, double remaining) {
	}

	/**
	 * @param target whether the bike should steer toward (targetX, targetZ) now
	 * @param prompt the open question for the HUD, or null
	 */
	public record Guidance(Phase phase, Prompt prompt, boolean target, double targetX, double targetZ) {
		public static final Guidance IDLE = new Guidance(Phase.IDLE, null, false, 0.0, 0.0);
	}

	/**
	 * @param promptEnabled whether the rider is asked at all
	 * @param cooldownTicks minimum time between two questions
	 * @param maxHalfWidth  widest road, as for {@link RoadFollower}
	 */
	public record Settings(boolean promptEnabled, int cooldownTicks, int maxHalfWidth) {
	}

	private static final int SCAN_INTERVAL = 4;
	/** Rescans stop this close to the junction; its shape is then taken as it was. */
	private static final double FREEZE_DISTANCE = 10.0;
	private static final int TURN_TIMEOUT_TICKS = 200;
	private static final int SUPPRESS_TICKS = 100;
	private static final double SUPPRESS_RADIUS = 12.0;
	private static final double SAME_WIDTH = 1.0;
	private static final double CHOICE_THRESHOLD = 0.25;

	private final Random random;

	private Phase phase = Phase.IDLE;
	private Junction junction;
	private Junction.Exit chosen;
	private Junction.Exit turning;
	private int tieBreaker;
	private long now;
	private long lastPrompt = Long.MIN_VALUE / 2;
	private long turnStart;
	private long nextScan;
	private long suppressUntil;
	private double suppressX;
	private double suppressZ;
	private double closest;
	private double promptStartDistance;
	private boolean neutralSeen;

	public JunctionNavigator(Random random) {
		this.random = random;
	}

	public Phase phase() {
		return this.phase;
	}

	/** Forgets any junction in progress, e.g. when the rider gets off. */
	public void reset() {
		this.phase = Phase.IDLE;
		this.junction = null;
		this.chosen = null;
		this.turning = null;
	}

	/**
	 * @param choice what the rider is steering right now: negative left, positive right, near zero nothing
	 */
	public Guidance update(RoadWorld world, double x, double y, double z, double yawDegrees, double speed, double choice,
		Settings settings, BikePhysics.Params params) {
		this.now++;
		int input = choice <= -CHOICE_THRESHOLD ? -1 : choice >= CHOICE_THRESHOLD ? 1 : 0;
		double lookAhead = RoadFollower.clamp(5.0 * speed + 6.0, 16.0, 40.0);
		double turnDistance = Math.max(7.0, speed * 0.9);

		if (this.phase == Phase.IDLE) {
			if (this.now >= this.nextScan) {
				this.nextScan = this.now + SCAN_INTERVAL;
				Junction found = JunctionScanner.scan(world, x, y, z, yawDegrees, settings.maxHalfWidth(), lookAhead + 4.0);
				if (found != null && !this.suppressed(found)) {
					this.junction = found;
					this.chosen = null;
					this.tieBreaker = this.random.nextInt(1 << 20);
					this.closest = Double.MAX_VALUE;
					this.phase = Phase.PENDING;
				}
			}
			return Guidance.IDLE;
		}

		double distance = Math.hypot(this.junction.centreX() - x, this.junction.centreZ() - z);
		if (this.phase != Phase.TURNING) {
			this.closest = Math.min(this.closest, distance);
			if (distance > this.closest + 6.0 || distance > lookAhead + 14.0) {
				this.reset();
				return Guidance.IDLE;
			}
			if (distance > FREEZE_DISTANCE && this.now >= this.nextScan) {
				this.nextScan = this.now + SCAN_INTERVAL;
				Junction again = JunctionScanner.scan(world, x, y, z, yawDegrees, settings.maxHalfWidth(), lookAhead + 4.0);
				if (again != null && Math.hypot(again.centreX() - this.junction.centreX(), again.centreZ() - this.junction.centreZ()) < 6.0) {
					this.junction = again;
				}
			}
		}

		if (this.phase == Phase.PENDING) {
			if (distance <= turnDistance) {
				this.beginTurn();
			} else if (distance <= lookAhead && this.canPrompt(settings)) {
				this.phase = Phase.PROMPTING;
				this.lastPrompt = this.now;
				this.promptStartDistance = Math.max(distance, turnDistance + 1.0);
				this.neutralSeen = false;
			}
		} else if (this.phase == Phase.PROMPTING) {
			if (input == 0) {
				this.neutralSeen = true;
			} else if (this.neutralSeen) {
				Junction.Exit pick = this.junction.exit(input < 0 ? Junction.Side.LEFT : Junction.Side.RIGHT);
				if (pick != null) {
					this.chosen = pick;
					this.neutralSeen = false;
				}
			}
			if (distance <= turnDistance) {
				this.beginTurn();
			}
		}

		if (this.phase == Phase.TURNING) {
			return this.turn(x, z, yawDegrees, speed, params);
		}
		Prompt prompt = null;
		if (this.phase == Phase.PROMPTING) {
			double remaining = (distance - turnDistance) / Math.max(1.0, this.promptStartDistance - turnDistance);
			prompt = new Prompt(this.junction.exit(Junction.Side.LEFT) != null, this.junction.exit(Junction.Side.RIGHT) != null,
				this.chosen == null ? null : this.chosen.side(), this.defaultExit().side(), RoadFollower.clamp(remaining, 0.0, 1.0));
		}
		return new Guidance(this.phase, prompt, false, 0.0, 0.0);
	}

	private boolean canPrompt(Settings settings) {
		boolean turnExists = this.junction.exit(Junction.Side.LEFT) != null || this.junction.exit(Junction.Side.RIGHT) != null;
		return settings.promptEnabled() && !this.junction.forced() && turnExists && this.now - this.lastPrompt >= settings.cooldownTicks();
	}

	private boolean suppressed(Junction found) {
		return this.now < this.suppressUntil && Math.hypot(found.centreX() - this.suppressX, found.centreZ() - this.suppressZ) < SUPPRESS_RADIUS;
	}

	/** The widest way on; among ways about as wide, one picked at random once per junction. */
	private Junction.Exit defaultExit() {
		double widest = 0.0;
		for (Junction.Exit exit : this.junction.exits()) {
			widest = Math.max(widest, exit.width());
		}
		List<Junction.Exit> candidates = new ArrayList<>();
		for (Junction.Exit exit : this.junction.exits()) {
			if (exit.width() >= widest - SAME_WIDTH) {
				candidates.add(exit);
			}
		}
		return candidates.get(this.tieBreaker % candidates.size());
	}

	private void beginTurn() {
		this.turning = this.chosen != null ? this.chosen : this.defaultExit();
		this.turnStart = this.now;
		this.phase = Phase.TURNING;
	}

	/**
	 * Tracks the path in along the approach, through the middle and out along the chosen way. The aim point is a
	 * lookahead down that path, as long as the bike needs to arc round a corner at its current speed.
	 */
	private Guidance turn(double x, double z, double yawDegrees, double speed, BikePhysics.Params params) {
		Junction.Exit exit = this.turning;
		double cx = this.junction.centreX();
		double cz = this.junction.centreZ();
		double ex = this.junction.entryX();
		double ez = this.junction.entryZ();
		double vx = x - cx;
		double vz = z - cz;
		double entryAt = vx * ex + vz * ez;
		double exitAt = vx * exit.dirX() + vz * exit.dirZ();
		double exitSide = vx * -exit.dirZ() + vz * exit.dirX();

		double yaw = Math.toRadians(yawDegrees);
		double alignment = -Math.sin(yaw) * exit.dirX() + Math.cos(yaw) * exit.dirZ();
		boolean aligned = exitAt >= this.junction.radius() && alignment > Math.cos(Math.toRadians(25.0)) && Math.abs(exitSide) < 3.0;
		boolean lost = Math.hypot(vx, vz) > this.junction.radius() + 12.0;
		if (aligned || lost || exitAt >= this.junction.radius() + 8.0 || this.now - this.turnStart > TURN_TIMEOUT_TICKS) {
			this.suppressX = cx;
			this.suppressZ = cz;
			this.suppressUntil = this.now + SUPPRESS_TICKS;
			this.reset();
			return Guidance.IDLE;
		}

		double turnRadius = speed / Math.max(0.2, Math.abs(BikePhysics.yawRate(Math.max(speed, 1.5), 1.0, params)));
		double lookahead = RoadFollower.clamp(1.2 * turnRadius, 3.5, 12.0);
		double aimX;
		double aimZ;
		if (entryAt < 0.0) {
			if (lookahead <= -entryAt) {
				aimX = cx + ex * (entryAt + lookahead);
				aimZ = cz + ez * (entryAt + lookahead);
			} else {
				aimX = cx + exit.dirX() * (lookahead + entryAt);
				aimZ = cz + exit.dirZ() * (lookahead + entryAt);
			}
		} else {
			double beyond = Math.max(exitAt, 0.0) + lookahead;
			aimX = cx + exit.dirX() * beyond;
			aimZ = cz + exit.dirZ() * beyond;
		}
		return new Guidance(Phase.TURNING, null, true, aimX, aimZ);
	}
}
