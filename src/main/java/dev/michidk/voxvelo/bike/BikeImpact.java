package dev.michidk.voxvelo.bike;

/**
 * What happens when a bike rides into a creature, with no Minecraft dependencies. Speeds are in m/s,
 * masses in kg. The closing speed (how fast the two approach each other) decides between a shove and a
 * damaging hit; the masses decide how far the creature flies and how much speed the bike loses.
 */
public final class BikeImpact {
	/** Closer than this is just touching. */
	public static final double TOUCH_SPEED = 0.8;
	/** From this closing speed on, the bike hurts what it hits; below it, it only shoves. */
	public static final double HIT_SPEED = 3.0;
	/** Mass of a creature the size of a player, for scaling other creatures by their size. */
	private static final double REFERENCE_MASS = 80.0;
	private static final double REFERENCE_VOLUME = 0.6 * 0.6 * 1.8;

	/**
	 * @param kind      what kind of contact this is
	 * @param damage    half hearts dealt (zero for a shove)
	 * @param knockback vanilla knockback power for a hit, or blocks per tick of shove for a shove
	 * @param speedLoss m/s the bike loses
	 */
	public record Outcome(Kind kind, float damage, double knockback, double speedLoss) {
		static final Outcome NONE = new Outcome(Kind.NONE, 0.0F, 0.0, 0.0);
	}

	public enum Kind {
		NONE, SHOVE, HIT
	}

	private BikeImpact() {
	}

	/** Rough mass of a creature from its bounding box: a cow weighs more than a chicken, a golem more than both. */
	public static double massOf(double width, double height) {
		return Math.max(5.0, Math.min(1500.0, REFERENCE_MASS * width * width * height / REFERENCE_VOLUME));
	}

	/**
	 * @param bikeSpeed    forward speed of the bike
	 * @param targetAlong  speed of the creature along the bike's heading (negative: coming towards the bike)
	 * @param bikeMass     bike and rider
	 * @param targetMass   the creature
	 */
	public static Outcome resolve(double bikeSpeed, double targetAlong, double bikeMass, double targetMass) {
		double closing = bikeSpeed - targetAlong;
		if (closing < TOUCH_SPEED) {
			return Outcome.NONE;
		}
		double share = targetMass / (bikeMass + targetMass);
		if (closing < HIT_SPEED) {
			// A shove is a perfectly inelastic push: both end up at the common speed, so a light creature is carried
			// along fast and a heavy one barely moves and drags the bike down. Its push is in blocks per tick.
			return new Outcome(Kind.SHOVE, 0.0F, closing * (1.0 - share) * BikeMotion.DT, Math.min(bikeSpeed, closing * share));
		}
		float damage = (float) Math.min(20.0, (closing - 2.5) * 0.7);
		double knockback = Math.min(1.6, (0.3 + 0.07 * closing) * Math.sqrt(Math.max(0.3, Math.min(1.5, bikeMass / targetMass))));
		return new Outcome(Kind.HIT, damage, knockback, Math.min(bikeSpeed, closing * share * 1.2));
	}
}
