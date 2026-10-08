package dev.michidk.voxvelo.client.fitness;

import dev.michidk.voxelfitness.api.*;
import dev.michidk.voxvelo.bike.BikeControlState;
import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.client.ftms.VirtualShifting;
import dev.michidk.voxvelo.client.input.BikeInputSource;
import dev.michidk.voxvelo.client.input.ControlChannel;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** The only vehicle coupling between the generic fitness runtime and VoxVelo. */
public final class BicycleFitnessAdapter implements VehicleAdapter, BikeInputSource {
	private final FitnessContext ctx;
	private FitnessControls input = FitnessControls.NONE;
	private int shifts;
	private Integer gear;

	public BicycleFitnessAdapter(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void tick(net.minecraft.client.Minecraft client) {}

	@Override
	public String id() {
		return "voxvelo:bicycle";
	}

	@Override
	public String displayName() {
		return "Fitness devices";
	}

	@Override
	public boolean supports(LocalPlayer player, Entity vehicle) {
		return vehicle instanceof BikeEntity bike && bike.getRider() == player;
	}

	@Override
	public VehicleTelemetry sample(LocalPlayer player, Entity vehicle) {
		BikeEntity bike = (BikeEntity) vehicle;
		ctx.rideTerrain.tick(net.minecraft.client.Minecraft.getInstance());
		return new VehicleTelemetry(
				bike.position(),
				bike.getSpeed(),
				ctx.core.sender().lastComposed().propulsion(),
				bike.isPedaling() ? bike.getCadenceRevsPerSecond() * 60 : 0,
				ctx.rideTerrain.slope().orElse(bike.getGradient()),
				bike.params().slopeGravityFactor(),
				bike.params().rolling(bike.getSurface()),
				bike.getBikeType().params().bikeMass() + ctx.core.config.riderMassKg,
				VirtualShifting.ratio(bike.getGear()),
				bike.getGear() + "/" + BikeControlState.MAX_GEAR);
	}

	@Override
	public void applyControls(LocalPlayer player, Entity vehicle, FitnessControls controls) {
		input = controls;
		shifts += controls.shift();
		gear = controls.gear();
	}

	@Override
	public void deactivate() {
		reset();
	}

	@Override
	public void reset() {
		input = FitnessControls.NONE;
		shifts = 0;
		gear = null;
	}

	@Override
	public boolean isActive() {
		return input != FitnessControls.NONE;
	}

	@Override
	public boolean supports(ControlChannel channel) {
		return true;
	}

	@Override
	public OptionalDouble propulsion() {
		return input.powerWatts() == null
				? OptionalDouble.empty()
				: OptionalDouble.of(input.powerWatts());
	}

	@Override
	public OptionalDouble steering() {
		return OptionalDouble.of(input.steering());
	}

	@Override
	public OptionalDouble brake() {
		return OptionalDouble.of(input.brake());
	}

	@Override
	public int consumeGearShift() {
		int result = shifts;
		shifts = 0;
		return result;
	}

	@Override
	public OptionalInt consumeGearSet() {
		Integer result = gear;
		gear = null;
		return result == null ? OptionalInt.empty() : OptionalInt.of(result);
	}
}
