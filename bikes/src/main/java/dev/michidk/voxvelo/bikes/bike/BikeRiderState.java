package dev.michidk.voxvelo.bikes.bike;

/**
 * What a rider's client reports about the bike it simulates, once per tick. The server keeps it for everyone else
 * (animation, HUDs, stats) and acts on the crash it reports; the position itself travels with vanilla vehicle movement.
 *
 * @param speed    forward speed, m/s
 * @param steering front wheel steering, -1 (left) .. +1 (right)
 * @param brake    0 .. 1
 * @param gear     selected gear
 * @param pedaling whether the cranks turn
 * @param power    rider power in watts
 * @param surface  {@link BikeSurface} ordinal under the wheels
 * @param gradient rise over run along the heading
 * @param impact   speed lost running into a wall this tick, m/s (zero without a crash)
 */
public record BikeRiderState(double speed, double steering, double brake, int gear, boolean pedaling, double power,
	int surface, double gradient, double impact) {

	/**
	 * Forces every field into its range; never trust values from the network. Speeds above {@code maxSpeed} (the
	 * bike's top speed with its enchantment) are cut down to it.
	 */
	public BikeRiderState sanitized(double maxSpeed) {
		double top = clamp(maxSpeed, 0.0, Double.MAX_VALUE);
		BikeControlState control = new BikeControlState(this.power, this.steering, this.brake, this.gear).sanitized();
		int knownSurface = this.surface >= 0 && this.surface < BikeSurface.values().length ? this.surface : BikeSurface.PATH.ordinal();
		return new BikeRiderState(clamp(this.speed, 0.0, top), control.steering(), control.brake(), control.gear(), this.pedaling,
			control.propulsion(), knownSurface, clamp(this.gradient, -BikeMotion.MAX_GRADIENT, BikeMotion.MAX_GRADIENT),
			clamp(this.impact, 0.0, top));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : 0.0;
	}
}
