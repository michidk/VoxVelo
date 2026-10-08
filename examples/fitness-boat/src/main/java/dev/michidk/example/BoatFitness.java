package dev.michidk.example;

import dev.michidk.voxvelo.fitnesslib.api.Fitness;
import dev.michidk.voxvelo.fitnesslib.api.FitnessControls;
import dev.michidk.voxvelo.fitnesslib.api.VehicleAdapter;
import dev.michidk.voxvelo.fitnesslib.api.VehicleTelemetry;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * Optional demonstration mod, not included in the three production jars. Vehicle physics stay here.
 */
public final class BoatFitness implements ClientModInitializer, VehicleAdapter {
	@Override
	public void onInitializeClient() {
		Fitness.register(this);
	}

	@Override
	public String id() {
		return "fitness_example:boat";
	}

	@Override
	public boolean supports(LocalPlayer player, Entity vehicle) {
		return vehicle instanceof AbstractBoat && vehicle.getControllingPassenger() == player;
	}

	@Override
	public void applyControls(LocalPlayer player, Entity vehicle, FitnessControls controls) {
		if (controls.powerWatts() == null) return;
		// Demonstration client prediction only; real vehicle mods should use their own
		// server-authoritative input packets.
		double acceleration = Math.min(.025, controls.powerWatts() / 20000);
		double yaw = Math.toRadians(vehicle.getYRot());
		vehicle.setDeltaMovement(
				vehicle.getDeltaMovement()
						.add(-Math.sin(yaw) * acceleration, 0, Math.cos(yaw) * acceleration)
						.scale(1 - controls.brake() * .2));
	}

	@Override
	public VehicleTelemetry sample(LocalPlayer player, Entity vehicle) {
		return new VehicleTelemetry(
				vehicle.position(),
				vehicle.getDeltaMovement().horizontalDistance() * 20,
				Fitness.controls().powerWatts() == null ? 0 : Fitness.controls().powerWatts(),
				Fitness.controls().cadenceRpm() == null ? 0 : Fitness.controls().cadenceRpm(),
				0,
				1,
				.004,
				100,
				0,
				"");
	}
}
