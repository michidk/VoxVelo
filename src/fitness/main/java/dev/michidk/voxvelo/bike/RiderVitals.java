package dev.michidk.voxvelo.bike;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each player has told the server about their heart rate and sharing choices. Values are validated here
 * and expire if the client stops refreshing them, so a disconnected trainer cannot leave a stale pulse on screen.
 */
public final class RiderVitals {
	/** Vitals older than this many server ticks are ignored. */
	public static final int EXPIRY_TICKS = 200;
	public static final int MIN_HEART_RATE = 30;
	public static final int MAX_HEART_RATE = 250;

	public record Vitals(boolean shareWatts, boolean shareHeartRate, int heartRate, int cadence, int updatedTick) {}

	private static final Map<UUID, Vitals> BY_PLAYER = new ConcurrentHashMap<>();

	private RiderVitals() {
	}

	/** Turns raw client values into safe ones: unknown flag bits are dropped, implausible heart rates become none. */
	public static Vitals sanitize(int flags, int heartRate, int cadence, int tick) {
		int hr = heartRate >= MIN_HEART_RATE && heartRate <= MAX_HEART_RATE ? heartRate : 0;
		return new Vitals((flags & 1) != 0, (flags & 2) != 0, hr, cadence == 65535 ? -1 : Math.max(0, Math.min(300, cadence)), tick);
	}

	public static void set(UUID player, Vitals vitals) {
		BY_PLAYER.put(player, vitals);
	}

	public static void forget(UUID player) {
		BY_PLAYER.remove(player);
	}

	/** The player's current vitals, or null if they never sent any or the last ones have expired. */
	public static Vitals fresh(UUID player, int currentTick) {
		Vitals vitals = BY_PLAYER.get(player);
		return vitals != null && currentTick - vitals.updatedTick() <= EXPIRY_TICKS ? vitals : null;
	}
}
