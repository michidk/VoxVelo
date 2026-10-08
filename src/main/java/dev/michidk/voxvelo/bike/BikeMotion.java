package dev.michidk.voxvelo.bike;

import dev.michidk.voxvelo.terrain.LevelGround;
import dev.michidk.voxvelo.terrain.TerrainSlope;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Moves a bike through the world for one tick: {@link BikePhysics} for the speed, then steering, slope, collisions
 * and ground following. It runs wherever the bike is simulated: on the rider's own client while ridden (like a
 * horse or a boat), on the server while parked.
 */
final class BikeMotion {
	static final double DT = 1.0 / 20.0;
	/** Steepest gradient the physics uses, as rise over run. */
	static final double MAX_GRADIENT = 0.6;
	/** Largest drop the bike stays glued to the ground over (down slopes, stairs). */
	private static final double GROUND_SNAP = 1.0;
	/** Blocks each way along the heading over which the terrain slope that drives gravity is measured. */
	private static final int SLOPE_PROBE_BLOCKS = 4;
	/** Share of the way to the measured slope the bike gradient moves per tick, so single steps do not jerk it. */
	private static final double SLOPE_SMOOTHING = 0.2;
	/** Rising more than this in one tick counts as hopping a ledge and costs speed. */
	private static final double LEDGE_PENALTY_HEIGHT = 0.55;

	/**
	 * @param surface    terrain under the wheels
	 * @param wallImpact speed lost running into a block, m/s
	 * @param hitCrash   speed lost to damaging hits on creatures, m/s
	 */
	record Step(BikeSurface surface, double wallImpact, double hitCrash) {
	}

	private final BikeEntity bike;
	private double speed;
	private double steering;
	private double gradient;

	BikeMotion(BikeEntity bike) {
		this.bike = bike;
	}

	double speed() {
		return this.speed;
	}

	double steering() {
		return this.steering;
	}

	double gradient() {
		return this.gradient;
	}

	/** Continues from what another side simulated, when this side takes the bike over. */
	void seed(double speed, double steering, double gradient) {
		this.speed = speed;
		this.steering = steering;
		this.gradient = gradient;
	}

	Step step(BikeControlState input, BikePhysics.Params params, RiderSettings.Settings rider, BikeEntityHits hits) {
		this.steering = BikePhysics.smoothSteering(this.steering, input.steering());

		// Gravity follows the slope under the wheels. It is read from the terrain along the heading, so it also works
		// at a standstill, over stairs and when rolling downhill, instead of being inferred from how far the bike moved.
		boolean probed = false;
		if (this.bike.onGround()) {
			double headingYaw = Math.toRadians(this.bike.getYRot());
			OptionalDouble probe = TerrainSlope.measureWindow(new LevelGround(this.bike.level()), this.bike.getX(), this.bike.getZ(),
				-Math.sin(headingYaw), Math.cos(headingYaw), this.bike.getY(), SLOPE_PROBE_BLOCKS);
			if (probe.isPresent()) {
				this.gradient += (Mth.clamp(probe.getAsDouble(), -MAX_GRADIENT, MAX_GRADIENT) - this.gradient) * SLOPE_SMOOTHING;
				probed = true;
			}
		}

		BikeSurface surface = BikeSurface.classify(
			this.bike.level().getBlockState(BlockPos.containing(this.bike.getX(), this.bike.getY() - 0.2, this.bike.getZ())),
			this.bike.isInWater());
		int boost = this.bike.getSpeedBoostLevel();
		this.speed = BikePhysics.stepSpeed(BikeSpeedBoost.baseSpeed(this.speed, boost), DT, input.propulsion(), input.brake(),
			surface, this.gradient, params);
		this.speed = this.limitByWheels(this.speed);
		this.speed = BikeSpeedBoost.apply(this.speed, boost);
		this.speed = RiderSettings.capSpeed(this.speed, rider);

		double yawRate = BikePhysics.yawRate(this.speed, this.steering, params);
		this.bike.setYRot(this.bike.getYRot() + (float) Math.toDegrees(yawRate * DT));
		this.bike.setXRot(0.0F);

		double yawRad = Math.toRadians(this.bike.getYRot());
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		BikeEntityHits.Result hit = hits.strike(this.speed, forwardX, forwardZ, params);
		this.speed = Math.max(0.0, this.speed - hit.speedLoss());
		double blocksPerTick = this.speed * DT;

		double verticalSpeed = this.bike.isInWater()
			? (this.bike.getDeltaMovement().y - 0.01) * 0.8
			: (this.bike.getDeltaMovement().y - this.bike.getGravity()) * 0.98;
		this.bike.setDeltaMovement(forwardX * blocksPerTick, verticalSpeed, forwardZ * blocksPerTick);

		double startX = this.bike.getX();
		double startY = this.bike.getY();
		double startZ = this.bike.getZ();
		boolean wasOnGround = this.bike.onGround();
		double speedBeforeMove = this.speed;
		this.bike.move(MoverType.SELF, this.bike.getDeltaMovement());

		// Collisions: keep the speed component that survived; the rest is the impact.
		double wallImpact = 0.0;
		if (this.bike.horizontalCollision) {
			Vec3 after = this.bike.getDeltaMovement();
			double surviving = Math.max(0.0, (after.x * forwardX + after.z * forwardZ) / DT);
			wallImpact = Math.max(0.0, speedBeforeMove - surviving);
			this.speed = surviving;
		}

		// Hopping up a tall ledge (mountain bike) is not free.
		if (this.bike.onGround() && this.bike.getY() - startY > LEDGE_PENALTY_HEIGHT) {
			this.speed *= this.bike.getBikeType().bumpSpeedFactor();
		}

		// Stay glued to the ground over down slopes and stairs instead of launching off them.
		if (wasOnGround && !this.bike.onGround() && this.bike.getDeltaMovement().y <= 0.0 && !this.bike.isInWater()) {
			this.bike.move(MoverType.SELF, new Vec3(0.0, -GROUND_SNAP, 0.0));
			if (this.bike.onGround()) {
				this.bike.setDeltaMovement(this.bike.getDeltaMovement().x, 0.0, this.bike.getDeltaMovement().z);
			}
		}
		this.bike.applyBlockEffects();

		// Without a terrain probe, the gradient comes from how the bike actually moved, smoothed so single stair
		// steps do not cause spikes.
		double moved = Math.hypot(this.bike.getX() - startX, this.bike.getZ() - startZ);
		if (!probed && this.bike.onGround()) {
			if (moved > 0.02) {
				double rawGradient = Mth.clamp((this.bike.getY() - startY) / moved, -MAX_GRADIENT, MAX_GRADIENT);
				this.gradient += (rawGradient - this.gradient) * 0.25;
			} else {
				this.gradient *= 0.8;
			}
		}
		return new Step(surface, wallImpact, hit.crash());
	}

	/** Burst tires drag the bike down to a crawl; a missing wheel stops it. */
	private double limitByWheels(double speed) {
		if (!this.bike.hasPart(BikeComponents.FRONT) || !this.bike.hasPart(BikeComponents.REAR)) {
			return 0.0;
		}
		int burst = (this.bike.tirePercent(BikeComponents.FRONT) == 0 ? 1 : 0) + (this.bike.tirePercent(BikeComponents.REAR) == 0 ? 1 : 0);
		return burst > 0 ? Math.min(3.0 / burst, speed * Math.pow(0.985, burst)) : speed;
	}
}
