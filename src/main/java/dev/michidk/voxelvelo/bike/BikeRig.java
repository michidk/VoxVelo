package dev.michidk.voxelvelo.bike;

/**
 * Measurements of a bike model that gameplay and rider posing depend on, taken from the model export metadata
 * (models/*.metadata.json). All lengths are in model units (16 per block, before {@link #MODEL_SCALE}), with
 * the front of the bike toward -Z and the ground at Y = 0.
 *
 * @param saddleY       height of the top of the saddle
 * @param saddleZ       position of the saddle along Z (positive is behind the origin)
 * @param gripX         half the distance between the grips
 * @param gripY         height of the grips
 * @param gripZ         position of the grips along Z
 * @param crankY        height of the bottom bracket
 * @param crankZ        position of the bottom bracket along Z
 * @param pedalRadius   distance of the pedals from the bottom bracket
 * @param pedalRestAngle angle of the right pedal at the start of the pedal animation, in radians from straight
 *                      down toward the front
 * @param steeringTilt  rake of the steering axis from vertical, in radians (top leans toward the rider)
 */
public record BikeRig(double saddleY, double saddleZ, double gripX, double gripY, double gripZ,
	double crankY, double crankZ, double pedalRadius, double pedalRestAngle, double steeringTilt) {

	/**
	 * The models are drawn at this fraction of their authored size. At full size the bike is 2 blocks long and the
	 * saddle 1.1 blocks up, which a player model (legs 0.75 blocks) can hardly pedal; at 0.8 it is 1.6 blocks long
	 * with the saddle at 0.88.
	 */
	public static final float MODEL_SCALE = 0.8F;

	private static double blocks(double units) {
		return units * MODEL_SCALE / 16.0;
	}

	/** Height of the rider hip above the bike origin, in blocks. */
	public double seatHeight() {
		return blocks(this.saddleY);
	}

	/** Seat position along the bike, in entity space (forward is positive), so a seat behind the origin is negative. */
	public double seatOffset() {
		return -blocks(this.saddleZ);
	}

	/** Horizontal distance from the hip forward to the grips, in blocks. */
	public double gripForward() {
		return blocks(this.saddleZ - this.gripZ);
	}

	/** Height of the grips relative to the hip, in blocks (negative: below the hip). */
	public double gripHeight() {
		return blocks(this.gripY - this.saddleY);
	}

	/** Horizontal distance from the hip forward to the bottom bracket, in blocks. */
	public double crankForward() {
		return blocks(this.saddleZ - this.crankZ);
	}

	/** Vertical distance from the hip down to the bottom bracket, in blocks. */
	public double crankDrop() {
		return blocks(this.saddleY - this.crankY);
	}

	public double pedalRadiusBlocks() {
		return blocks(this.pedalRadius);
	}
}
