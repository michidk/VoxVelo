package dev.michidk.voxvelo.bikes.bike;

/**
 * Pure bicycle dynamics with no Minecraft dependencies. Speeds are in m/s (1 block = 1 m),
 * forces in newtons, power in watts.
 */
public final class BikePhysics {
	public static final double GRAVITY = 9.81;
	public static final double AIR_DENSITY = 1.225;

	/** Below this speed the drive force is computed as if the bike moved at this speed (avoids P/0). */
	private static final double MIN_DRIVE_SPEED = 1.5;

	/**
	 * @param riderMass            kg
	 * @param bikeMass             kg
	 * @param maxSpeed             hard cap, m/s
	 * @param dragArea             CdA, m^2
	 * @param drivetrainEfficiency 0..1
	 * @param maxDriveAcceleration m/s^2 limit so huge watts at low speed cannot launch the bike
	 * @param maxBrakeDeceleration m/s^2 at full brake on a grippy surface
	 * @param slopeGravityFactor   game-balance scale on the gravity component along the slope (Minecraft slopes are steep)
	 * @param wheelBase            m
	 * @param steerAngleLowSpeed   degrees of front wheel lock at walking pace
	 * @param steerAngleHighSpeed  degrees of front wheel lock at high speed
	 * @param maxLateralAccel      m/s^2 cornering limit
	 * @param pavedRolling         tyre factor on Crr for hard surfaces
	 * @param looseRolling         tyre factor on Crr for loose surfaces
	 * @param looseGrip            tyre grip factor on loose surfaces (1 = neutral)
	 */
	public record Params(
		double riderMass,
		double bikeMass,
		double maxSpeed,
		double dragArea,
		double drivetrainEfficiency,
		double maxDriveAcceleration,
		double maxBrakeDeceleration,
		double slopeGravityFactor,
		double wheelBase,
		double steerAngleLowSpeed,
		double steerAngleHighSpeed,
		double maxLateralAccel,
		double pavedRolling,
		double looseRolling,
		double looseGrip
	) {
		public double totalMass() {
			return riderMass + bikeMass;
		}

		public Params withRiderMass(double mass) {
			return new Params(mass, bikeMass, maxSpeed, dragArea, drivetrainEfficiency, maxDriveAcceleration, maxBrakeDeceleration,
				slopeGravityFactor, wheelBase, steerAngleLowSpeed, steerAngleHighSpeed, maxLateralAccel, pavedRolling, looseRolling, looseGrip);
		}

		public Params withMaxSpeed(double speed) {
			return new Params(riderMass, bikeMass, speed, dragArea, drivetrainEfficiency, maxDriveAcceleration, maxBrakeDeceleration,
				slopeGravityFactor, wheelBase, steerAngleLowSpeed, steerAngleHighSpeed, maxLateralAccel, pavedRolling, looseRolling, looseGrip);
		}

		public Params withTires(double paved, double loose, double grip) {
			return new Params(riderMass, bikeMass, maxSpeed, dragArea, drivetrainEfficiency, maxDriveAcceleration, maxBrakeDeceleration,
				slopeGravityFactor, wheelBase, steerAngleLowSpeed, steerAngleHighSpeed, maxLateralAccel, paved, loose, grip);
		}

		public Params withCockpit(double drag, double steerLow, double steerHigh) {
			return new Params(riderMass, bikeMass, maxSpeed, drag, drivetrainEfficiency, maxDriveAcceleration, maxBrakeDeceleration,
				slopeGravityFactor, wheelBase, steerLow, steerHigh, maxLateralAccel, pavedRolling, looseRolling, looseGrip);
		}

		/** Effective rolling resistance coefficient of this bike on the given surface. */
		public double rolling(BikeSurface surface) {
			return surface.rollingResistance * lerp(pavedRolling, looseRolling, surface.looseness);
		}

		/** Effective fraction of drive/brake force this bike can transfer on the given surface. */
		public double grip(BikeSurface surface) {
			return Math.min(1.0, surface.traction * lerp(1.0, looseGrip, surface.looseness));
		}
	}

	private BikePhysics() {
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/**
	 * Advances the forward speed by one step.
	 *
	 * @param speed    current forward speed, m/s (never negative)
	 * @param dt       step length in seconds
	 * @param power    rider power in watts
	 * @param brake    0..1
	 * @param surface  surface under the bike
	 * @param gradient rise over run along the direction of travel (0.1 = 10 % uphill)
	 * @return the new forward speed, m/s, within {@code [0, maxSpeed]}
	 */
	public static double stepSpeed(double speed, double dt, double power, double brake, BikeSurface surface, double gradient, Params p) {
		double mass = p.totalMass();
		double sinSlope = gradient / Math.sqrt(1.0 + gradient * gradient);
		double cosSlope = 1.0 / Math.sqrt(1.0 + gradient * gradient);
		double grip = p.grip(surface);

		double driveForce = Math.min(
			p.drivetrainEfficiency() * power / Math.max(speed, MIN_DRIVE_SPEED),
			mass * p.maxDriveAcceleration() * grip
		);
		double aeroForce = 0.5 * AIR_DENSITY * p.dragArea() * speed * speed;
		double gradientForce = mass * GRAVITY * sinSlope * p.slopeGravityFactor();

		double newSpeed = speed + (driveForce - aeroForce - gradientForce) / mass * dt;

		// Rolling resistance and braking only ever remove speed; they must not push it through zero.
		double rollingForce = p.rolling(surface) * mass * GRAVITY * cosSlope;
		double brakeDeceleration = brake * p.maxBrakeDeceleration() * grip;
		double friction = (rollingForce / mass + brakeDeceleration) * dt;
		if (newSpeed > 0.0) {
			newSpeed = Math.max(0.0, newSpeed - friction);
		}

		return Math.max(0.0, Math.min(p.maxSpeed(), newSpeed));
	}

	/** Maximum front wheel steering angle in radians; large at walking pace, small when fast. */
	public static double maxSteerAngle(double speed, Params p) {
		double t = Math.max(0.0, Math.min(1.0, (speed - 2.0) / 12.0));
		t = t * t * (3.0 - 2.0 * t);
		return Math.toRadians(lerp(p.steerAngleLowSpeed(), p.steerAngleHighSpeed(), t));
	}

	/**
	 * Kinematic bicycle yaw rate in radians per second for a normalized steering value.
	 * Positive steering (right) turns clockwise seen from above, which increases the Minecraft yaw.
	 * At a standstill the bike can still pivot slowly so tight spaces remain practical.
	 */
	public static double yawRate(double speed, double steering, Params p) {
		double effectiveSpeed = Math.max(speed, 1.2);
		double angle = steering * maxSteerAngle(speed, p);
		double rate = effectiveSpeed * Math.tan(angle) / p.wheelBase();
		double limit = p.maxLateralAccel() / effectiveSpeed;
		rate = Math.max(-limit, Math.min(limit, rate));
		double pivot = 0.35 + 0.65 * Math.min(1.0, speed / 1.5);
		return rate * pivot;
	}

	/** Smooths the actual steering toward the commanded value; centering is faster than turning in. */
	public static double smoothSteering(double current, double target) {
		return smoothSteering(current, target, 0.2, 0.35);
	}

	/** Moves steering toward {@code target} by at most {@code turnIn} per step, or {@code centering} on the way back. */
	public static double smoothSteering(double current, double target, double turnIn, double centering) {
		boolean returning = Math.abs(target) < Math.abs(current) || Math.signum(target) != Math.signum(current);
		double maxChange = returning ? centering : turnIn;
		return current + Math.max(-maxChange, Math.min(maxChange, target - current));
	}
}
