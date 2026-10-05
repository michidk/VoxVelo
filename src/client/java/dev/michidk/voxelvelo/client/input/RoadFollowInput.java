package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import dev.michidk.voxelvelo.client.road.ClientRoadWorld;
import dev.michidk.voxelvelo.client.road.RoadBlocks;
import dev.michidk.voxelvelo.client.road.RoadFollower;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Steering that keeps the bike on the centre of a road made of the player's chosen blocks. It only steers: for
 * hands-off riding pair it with trainer or keyboard propulsion. It is active while a road centre can be found
 * ahead; when there is none (off the road, a dead end, open ground) the controls fall back to the keyboard.
 */
public final class RoadFollowInput implements BikeInputSource {
	private static final double SLEW_PER_TICK = 0.3;
	/** Below this speed the bike would only pivot on the spot, so no steering is applied. */
	private static final double MIN_SPEED = 0.5;

	private final VoxelVeloConfig config;
	private final RoadBlocks roadBlocks = new RoadBlocks();
	private boolean active;
	private double steering;

	public RoadFollowInput(VoxelVeloConfig config) {
		this.config = config;
	}

	@Override
	public String id() {
		return "roadfollow";
	}

	@Override
	public boolean supports(ControlChannel channel) {
		return channel == ControlChannel.STEERING;
	}

	@Override
	public String displayName() {
		return "Road follow";
	}

	@Override
	public boolean isActive() {
		return this.active;
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (client.level == null || player == null || !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			this.reset();
			return;
		}
		this.roadBlocks.update(this.config.roadBlocks);
		RoadFollower.Result result = RoadFollower.steer(
			new ClientRoadWorld(client.level, this.roadBlocks),
			bike.getX(), bike.getY(), bike.getZ(), bike.getYRot(), bike.getSpeed(),
			bike.getBikeType().params(), this.config.roadMaxHalfWidth);
		this.active = result.hasTarget();
		double target = this.active && bike.getSpeed() >= MIN_SPEED ? result.steering() : 0.0;
		this.steering += Math.max(-SLEW_PER_TICK, Math.min(SLEW_PER_TICK, target - this.steering));
	}

	@Override
	public void reset() {
		this.active = false;
		this.steering = 0.0;
	}

	@Override
	public OptionalDouble propulsion() {
		return OptionalDouble.empty();
	}

	@Override
	public OptionalDouble steering() {
		return OptionalDouble.of(this.steering);
	}

	@Override
	public OptionalDouble brake() {
		return OptionalDouble.empty();
	}

	@Override
	public int consumeGearShift() {
		return 0;
	}
}
