package dev.michidk.voxvelo.bikes.bike;

import java.util.UUID;

/** Server movement converted to vanilla centimetres without losing slow movement to rounding. */
public final class BikeRideDistance {
	private UUID rider;
	private double x;
	private double z;
	private double remainder;

	public int sample(UUID currentRider, double nextX, double nextZ) {
		int centimetres = 0;
		if (currentRider != null && currentRider.equals(this.rider)) {
			double moved = Math.hypot(nextX - this.x, nextZ - this.z);
			// Ignore teleports and invalid movement, matching the tire-wear movement limit.
			if (Double.isFinite(moved) && moved < BikeServerTick.MAX_RIDE_STEP) {
				double total = this.remainder + moved * 100.0;
				centimetres = (int) total;
				this.remainder = total - centimetres;
			}
		} else {
			this.remainder = 0.0;
		}
		this.rider = currentRider;
		this.x = nextX;
		this.z = nextZ;
		return centimetres;
	}
}
