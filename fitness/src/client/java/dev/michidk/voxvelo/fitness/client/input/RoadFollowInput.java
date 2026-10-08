package dev.michidk.voxvelo.fitness.client.input;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.client.input.BikeInputManager;
import dev.michidk.voxvelo.bikes.client.input.BikeInputSource;
import dev.michidk.voxvelo.bikes.client.input.ControlChannel;
import dev.michidk.voxvelo.fitness.client.road.ClientRoadWorld;
import dev.michidk.voxvelo.fitness.client.road.JunctionNavigator;
import dev.michidk.voxvelo.fitness.client.road.RoadBlocks;
import dev.michidk.voxvelo.fitness.client.road.RoadFollower;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Steering that keeps the bike on the centre of a road made of the player's chosen blocks, when the player has
 * turned road following on. It only steers: for hands-off riding pair it with trainer or keyboard propulsion. It is
 * active while a road centre can be found ahead; when there is none (off the road, a dead end, open ground) the
 * other sources steer.
 *
 * <p>At a junction it asks the rider for left or right (steering on any other source answers) and takes the
 * widest way on if there is no answer; see {@link JunctionNavigator}.
 */
public final class RoadFollowInput implements BikeInputSource {
	private static final double SLEW_PER_TICK = 0.3;
	/** Below this speed the bike would only pivot on the spot, so no steering is applied. */
	private static final double MIN_SPEED = 0.5;

	private final FitnessConfig config;
	private final BikeInputManager manager;
	private final RoadBlocks roadBlocks = new RoadBlocks();
	private final JunctionNavigator navigator = new JunctionNavigator(new Random());
	private JunctionNavigator.Guidance guidance = JunctionNavigator.Guidance.IDLE;
	private boolean active;
	private double steering;

	public RoadFollowInput(FitnessConfig config, BikeInputManager manager) {
		this.config = config;
		this.manager = manager;
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

	/** The open left/right question for the HUD, or null. */
	public JunctionNavigator.Prompt prompt() {
		return this.guidance.prompt();
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (!this.config.roadFollowEnabled || client.level == null || player == null
				|| !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			this.reset();
			return;
		}
		this.roadBlocks.update(this.config.roadBlocks);
		ClientRoadWorld world = new ClientRoadWorld(client.level, this.roadBlocks);
		var params = bike.params();
		double speed = bike.getSpeed();
		RoadFollower.Result result = RoadFollower.steer(world, bike.getX(), bike.getY(), bike.getZ(), bike.getYRot(), speed, params, this.config.roadMaxHalfWidth);

		JunctionNavigator.Settings settings = new JunctionNavigator.Settings(this.config.junctionPrompt,
			this.config.junctionCooldownSeconds * 20, this.config.roadMaxHalfWidth);
		this.guidance = this.navigator.update(world, bike.getX(), bike.getY(), bike.getZ(), bike.getYRot(), speed, this.riderChoice(), settings, params);

		double target = 0.0;
		if (this.guidance.target()) {
			this.active = true;
			if (speed >= MIN_SPEED) {
				target = RoadFollower.steerToward(bike.getX(), bike.getZ(), bike.getYRot(), speed, params, this.guidance.targetX(), this.guidance.targetZ());
			}
		} else if (this.guidance.phase() != JunctionNavigator.Phase.IDLE) {
			// Approaching a junction the road ahead is no longer one clean run: carry on until the turn starts.
			this.active = true;
			if (result.hasTarget() && speed >= MIN_SPEED) {
				target = result.steering();
			}
		} else {
			this.active = result.hasTarget();
			if (this.active && speed >= MIN_SPEED) {
				target = result.steering();
			}
		}
		this.steering += Math.max(-SLEW_PER_TICK, Math.min(SLEW_PER_TICK, target - this.steering));
	}

	/** What the rider is steering with the keyboard or another source: negative left, positive right. */
	private double riderChoice() {
		double strongest = 0.0;
		for (BikeInputSource source : this.manager.sources()) {
			if (source != this && source.supports(ControlChannel.STEERING) && source.isActive()) {
				double value = source.steering().orElse(0.0);
				if (Math.abs(value) > Math.abs(strongest)) {
					strongest = value;
				}
			}
		}
		return strongest;
	}

	@Override
	public void reset() {
		this.navigator.reset();
		this.guidance = JunctionNavigator.Guidance.IDLE;
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
