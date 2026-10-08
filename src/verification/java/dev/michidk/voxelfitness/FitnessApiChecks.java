package dev.michidk.voxelfitness;

import dev.michidk.voxelfitness.api.*;
import dev.michidk.voxelfitness.client.VehicleBinding;
import dev.michidk.voxvelo.client.ftms.TrainerSimulation;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Boundary tests with a second vehicle's own physics, without a bike entity or real Bluetooth. */
public final class FitnessApiChecks {
	private static final class RowingVehicle implements VehicleAdapter {
		private double speed;
		private int releases;

		@Override
		public String id() {
			return "example:rowing";
		}

		@Override
		public boolean supports(LocalPlayer player, Entity vehicle) {
			return true;
		}

		@Override
		public void applyControls(LocalPlayer player, Entity vehicle, FitnessControls controls) {
			speed = controls.powerWatts() == null ? 0 : Math.sqrt(controls.powerWatts()) * .1;
			speed *= 1 - controls.brake();
		}

		@Override
		public VehicleTelemetry sample(LocalPlayer player, Entity vehicle) {
			return new VehicleTelemetry(new Vec3(1, 2, 3), speed, 100, 60, .1, 1, .01, 100, 0, "");
		}

		@Override
		public void deactivate() {
			releases++;
			speed = 0;
		}
	}

	public static void main(String[] args) throws Exception {
		VehicleRegistry registry = new VehicleRegistry();
		RowingVehicle rowing = new RowingVehicle();
		AutoCloseable registration = registry.register(rowing);
		require(
				registry.select(a -> a.supports(null, null)) == rowing,
				"non-bike vehicle registration");
		require(registry.select(a -> false) == null, "unmatched vehicles ignored");
		boolean duplicate = false;
		try {
			registry.register(new RowingVehicle());
		} catch (IllegalArgumentException e) {
			duplicate = true;
		}
		require(duplicate, "duplicate IDs rejected");
		int[] neutral = {0};
		VehicleBinding binding = new VehicleBinding(() -> neutral[0]++);
		Object first = new Object(), second = new Object();
		binding.select(first, rowing);
		rowing.applyControls(null, null, new FitnessControls(1, 100.0, 60.0, 120, .5, 0, 1, null));
		require(rowing.sample(null, null).speedMps() == 1, "vehicle chooses its own power model");
		binding.select(first, rowing);
		require(rowing.releases == 0, "same vehicle remains active");
		binding.select(second, rowing);
		require(
				rowing.releases == 1 && neutral[0] == 2,
				"vehicle replacement releases controls and feedback");
		binding.clear();
		require(
				rowing.releases == 2 && binding.adapter() == null && binding.identity() == null,
				"pause/dismount clears vehicle");
		registration.close();
		require(registry.select(a -> true) == null, "unregistration removes adapter");
		var controls =
				new FitnessControls(
						2, Double.NaN, Double.POSITIVE_INFINITY, 0, Double.NaN, 4, 999, -1);
		require(
				controls.powerWatts() == null
						&& controls.cadenceRpm() == null
						&& controls.heartRateBpm() == null,
				"invalid device measurements unavailable");
		require(
				controls.steering() == 0
						&& controls.brake() == 1
						&& controls.shift() == 100
						&& controls.gear() == null,
				"controls sanitized");
		var telemetry =
				new VehicleTelemetry(
						Vec3.ZERO,
						Double.NaN,
						-3,
						Double.NaN,
						Double.NaN,
						1,
						.4,
						Double.NaN,
						0,
						null);
		require(
				telemetry.speedMps() == 0 && telemetry.gradient() == 0 && telemetry.massKg() == 85,
				"vehicle measurements sanitized");
		boolean invalid = false;
		try {
			new VehicleTelemetry(new Vec3(Double.NaN, 0, 0), 0, 0, 0, 0, 1, 0, 85, 0, "");
		} catch (IllegalArgumentException e) {
			invalid = true;
		}
		require(invalid, "invalid positions rejected");
		var resistance = TrainerSimulation.target(100, .4, 10, 2);
		require(
				resistance.gradePercent() <= 15 && resistance.crr() <= .0255,
				"custom vehicle feedback bounded");
		require(
				TrainerSimulation.target(Double.NaN, Double.NaN, 1, 1).gradePercent() == 0,
				"invalid grade neutral");
		System.out.println("Generic fitness API and second-vehicle boundary checks passed.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
