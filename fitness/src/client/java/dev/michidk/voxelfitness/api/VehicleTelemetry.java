package dev.michidk.voxelfitness.api;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** SI units: blocks/metres, m/s, watts, rpm, rise/run, kg. Ratio 0 disables virtual shifting. */
public record VehicleTelemetry(
		Vec3 position,
		double speedMps,
		double powerWatts,
		double cadenceRpm,
		double gradient,
		double slopeScale,
		double rollingResistance,
		double massKg,
		double virtualGearRatio,
		String gearLabel) {
	public VehicleTelemetry {
		Objects.requireNonNull(position);
		if (!Double.isFinite(position.x)
				|| !Double.isFinite(position.y)
				|| !Double.isFinite(position.z)) {
			throw new IllegalArgumentException("Vehicle position must be finite");
		}
		speedMps = bounded(speedMps, 0, 1000, 0);
		powerWatts = bounded(powerWatts, 0, 3000, 0);
		cadenceRpm = bounded(cadenceRpm, 0, 254, 0);
		gradient = bounded(gradient, -100, 100, 0);
		slopeScale = bounded(slopeScale, 0, 10, 0);
		rollingResistance = bounded(rollingResistance, 0, 1, 0);
		massKg = bounded(massKg, 1, 10000, 85);
		virtualGearRatio = bounded(virtualGearRatio, 0, 100, 0);
		gearLabel = Objects.requireNonNullElse(gearLabel, "");
	}

	private static double bounded(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}
}
