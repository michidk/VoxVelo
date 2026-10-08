package dev.michidk.voxvelo.bikes.bike;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player preferences that shape the physics of the bike that player rides. They are sent by the
 * player client, validated here, and forgotten when the player disconnects.
 */
public final class RiderSettings {
	public static final double DEFAULT_MASS_KG = 75.0;
	public static final double MIN_MASS_KG = 40.0;
	public static final double MAX_MASS_KG = 150.0;
	/** Personal speed limit in km/h; 0 means no limit beyond the bike type's own top speed. */
	public static final double MIN_LIMIT_KMH = 5.0;
	public static final double MAX_LIMIT_KMH = 100.0;

	public record Settings(double massKg, double speedLimitKmh) {
		public static final Settings DEFAULT = new Settings(DEFAULT_MASS_KG, 0.0);
	}

	private static final Map<UUID, Settings> BY_PLAYER = new ConcurrentHashMap<>();

	private RiderSettings() {
	}

	/** Clamps arbitrary client values into the allowed ranges; NaN and infinities fall back to the defaults. */
	public static Settings sanitize(double massKg, double speedLimitKmh) {
		double mass = Double.isFinite(massKg) ? Math.max(MIN_MASS_KG, Math.min(MAX_MASS_KG, massKg)) : DEFAULT_MASS_KG;
		double limit = Double.isFinite(speedLimitKmh) && speedLimitKmh >= MIN_LIMIT_KMH
			? Math.min(MAX_LIMIT_KMH, speedLimitKmh)
			: 0.0;
		return new Settings(mass, limit);
	}

	public static void set(UUID player, Settings settings) {
		BY_PLAYER.put(player, settings);
	}

	public static Settings get(UUID player) {
		return BY_PLAYER.getOrDefault(player, Settings.DEFAULT);
	}

	public static void forget(UUID player) {
		BY_PLAYER.remove(player);
	}

	/** The bike type's physics adjusted for this rider. */
	public static BikePhysics.Params apply(BikePhysics.Params base, Settings settings) {
		BikePhysics.Params params = base.withRiderMass(settings.massKg());
		if (settings.speedLimitKmh() > 0.0) {
			params = params.withMaxSpeed(Math.min(base.maxSpeed(), settings.speedLimitKmh() / 3.6));
		}
		return params;
	}

	/** Applies the rider's optional absolute limit after other movement-speed modifiers. */
	public static double capSpeed(double speed, Settings settings) {
		return settings.speedLimitKmh() > 0.0
			? Math.min(speed, settings.speedLimitKmh() / 3.6)
			: speed;
	}
}
