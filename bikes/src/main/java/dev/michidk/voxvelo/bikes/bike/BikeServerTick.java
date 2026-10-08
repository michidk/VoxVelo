package dev.michidk.voxvelo.bikes.bike;

import dev.michidk.voxvelo.bikes.registry.ModStats;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The server's share of a bike's tick: it validates what the rider's client reports and publishes it to everyone
 * else, hurts what the bike runs into, wears the tires by the distance covered, throws the rider off a bike that is
 * under water and keeps the riding statistics.
 */
final class BikeServerTick {
	/** Moving further than this in one tick is a teleport, not riding: it wears no tire and counts for no statistic. */
	static final double MAX_RIDE_STEP = 4.0;
	/** Server ticks without a rider report after which the rider's power counts as zero. */
	private static final int STATE_TIMEOUT_TICKS = 20;
	private static final int SUBMERGED_EJECT_TICKS = 40;
	/**
	 * How much faster than the server saw it move (m/s) a rider's client may say its bike goes when the server works
	 * out what the bike ran into. Movement packets do not arrive exactly once per tick, so the observed speed jitters.
	 */
	private static final double OBSERVED_SPEED_TOLERANCE = 1.5;
	// Synced motion is rounded to these steps per unit, so small changes do not resend the entity data every tick.
	private static final double SPEED_STEPS = 10.0;
	private static final double STEERING_STEPS = 16.0;
	private static final double BRAKE_STEPS = 8.0;
	private static final double GRADIENT_STEPS = 100.0;

	private final BikeEntity bike;
	private final BikeWear wear;
	private final BikeEntityHits hits;
	private final BikeRideDistance rideDistance = new BikeRideDistance();
	private double reportedPower;
	private int lastReportTick = Integer.MIN_VALUE / 2;
	private double pendingImpact;
	private int submergedTicks;
	private @Nullable Vec3 lastPos;
	/** Horizontal speed the server saw the bike move at in the previous tick, m/s. */
	private double lastObservedSpeed;

	BikeServerTick(BikeEntity bike, BikeWear wear, BikeEntityHits hits) {
		this.bike = bike;
		this.wear = wear;
		this.hits = hits;
	}

	/** @param riderSimulates whether the rider's client moved the bike this tick rather than the server */
	void tick(boolean riderSimulates) {
		this.bike.getEntityData().set(BikeEntity.DATA_STEP_HEIGHT, (float) BikeServerConfig.get().stepHeightBlocks);
		Player rider = this.bike.getRider();
		if (rider != null && !this.bike.canRide()) {
			this.bike.ejectPassengers();
		}
		if (this.bike.isEyeInFluid(FluidTags.WATER)) {
			if (++this.submergedTicks >= SUBMERGED_EJECT_TICKS) {
				this.bike.ejectPassengers();
			}
		} else {
			this.submergedTicks = 0;
		}

		Vec3 position = this.bike.position();
		double moved = this.lastPos == null ? 0.0 : Math.hypot(position.x - this.lastPos.x, position.z - this.lastPos.z);
		boolean rode = this.lastPos != null && moved < MAX_RIDE_STEP;
		double observedSpeed = rode ? moved / BikeMotion.DT : 0.0;

		if (riderSimulates) {
			// The rider's client moved the bike; the server deals with what it ran into and how hard it crashed. The
			// reported speed only counts as far as the bike was seen to move (this tick or the last, as packets
			// bunch up), so a client cannot claim speed to hurt whatever stands next to it.
			double speed = Math.min(this.bike.getSpeed(), Math.max(observedSpeed, this.lastObservedSpeed) + OBSERVED_SPEED_TOLERANCE);
			double yawRad = Math.toRadians(this.bike.getYRot());
			BikePhysics.Params params = rider != null ? RiderSettings.apply(this.bike.params(), RiderSettings.get(rider.getUUID())) : this.bike.params();
			this.wear.crash(this.hits.strike(speed, -Math.sin(yawRad), Math.cos(yawRad), params).crash());
			this.wear.crash(this.pendingImpact);
		}
		this.pendingImpact = 0.0;
		this.lastObservedSpeed = observedSpeed;

		// Tires wear by the distance actually covered, whichever side moved the bike.
		if (rode && rider != null && this.bike.onGround()) {
			this.wear.ride(moved, this.bike.getSurface());
		}
		this.lastPos = position;
		// Recheck after ejection: only the player still riding receives statistics.
		Player statsRider = this.bike.getRider();
		int centimetres = this.rideDistance.sample(statsRider == null ? null : statsRider.getUUID(), position.x, position.z);
		if (statsRider != null) {
			statsRider.awardStat(ModStats.TIME_ON_BICYCLE);
			if (centimetres > 0) statsRider.awardStat(ModStats.DISTANCE_CYCLED, centimetres);
		}
	}

	/** The rider client's report of the bike it simulates. Values are validated here. */
	void applyRiderState(BikeRiderState report) {
		double maxSpeed = this.bike.params().maxSpeed() * BikeSpeedBoost.multiplier(this.bike.getSpeedBoostLevel());
		BikeRiderState state = report.sanitized(maxSpeed);
		boolean drives = this.bike.canRide() && this.bike.hasPart(BikeComponents.PEDALS);
		this.publishMotion(state.speed(), state.steering(), state.brake(), state.gear(), drives && state.pedaling(),
			state.gradient(), BikeSurface.byOrdinal(state.surface()));
		this.reportedPower = drives ? state.power() : 0.0;
		this.lastReportTick = this.bike.tickCount;
		this.pendingImpact = Math.max(this.pendingImpact, state.impact());
	}

	/** Watts the rider last reported, or zero once the reports have stopped. */
	double reportedPower() {
		return this.bike.tickCount - this.lastReportTick > STATE_TIMEOUT_TICKS ? 0.0 : this.reportedPower;
	}

	/** Syncs the ride state to every client, for animation and HUDs. */
	void publishMotion(double speed, double steering, double brake, int gear, boolean pedaling, double gradient, BikeSurface surface) {
		SynchedEntityData data = this.bike.getEntityData();
		data.set(BikeEntity.DATA_SPEED, quantize(speed, SPEED_STEPS));
		data.set(BikeEntity.DATA_STEERING, quantize(steering, STEERING_STEPS));
		data.set(BikeEntity.DATA_BRAKE, quantize(brake, BRAKE_STEPS));
		data.set(BikeEntity.DATA_GRADIENT, quantize(gradient, GRADIENT_STEPS));
		data.set(BikeEntity.DATA_GEAR, gear);
		data.set(BikeEntity.DATA_PEDALING, pedaling);
		data.set(BikeEntity.DATA_SURFACE, surface.ordinal());
	}

	private static float quantize(double value, double steps) {
		return (float) (Math.round(value * steps) / steps);
	}
}
