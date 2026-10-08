package dev.michidk.voxvelo.bike;

import org.jspecify.annotations.Nullable;

/**
 * The rider's own client as the bike sees it while that client simulates the ride, like a horse or a boat. The client
 * entrypoint installs it with {@link BikeEntity#setDriver}; a dedicated server never has one.
 */
public interface BikeDriver {
	/** The controls composed for this bike, or null until the client has taken the bike over. */
	@Nullable BikeControlState input(BikeEntity bike);

	/** The local player's rider mass and speed limit. */
	RiderSettings.Settings riderSettings();

	/** Tells the server what the locally simulated bike did this tick. */
	void report(BikeEntity bike, BikeRiderState state);
}
