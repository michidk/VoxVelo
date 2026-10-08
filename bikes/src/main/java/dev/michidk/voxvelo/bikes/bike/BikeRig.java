package dev.michidk.voxvelo.bikes.bike;

/**
 * Measurements of a bike model that gameplay and rider posing depend on, taken from the model export metadata
 * (models/{road,gravel,mountain}_bike.json). All lengths are in model units (16 per block, before {@link #MODEL_SCALE}), with
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
 * @param headY         height of the top of the head tube, where the handlebars clamp (the fork bone pivot)
 * @param headZ         position of the top of the head tube along Z
 */
public record BikeRig(double saddleY, double saddleZ, double gripX, double gripY, double gripZ,
	double crankY, double crankZ, double pedalRadius, double pedalRestAngle, double steeringTilt, double headY, double headZ) {

	/**
	 * The models are drawn at this fraction of their authored size. At 0.9 the bikes are about 1.8 blocks long, their
	 * saddles are about 0.95-0.99 blocks high, and their wheels have a realistic radius of roughly 0.35 blocks.
	 */
	public static final float MODEL_SCALE = 0.9F;

	/** This frame with the handlebars of another bike: its grips keep their place relative to the head tube. */
	public BikeRig withBarsOf(BikeRig bars) {
		return new BikeRig(this.saddleY, this.saddleZ, bars.gripX, this.headY + bars.gripY - bars.headY, this.headZ + bars.gripZ - bars.headZ,
			this.crankY, this.crankZ, this.pedalRadius, this.pedalRestAngle, this.steeringTilt, this.headY, this.headZ);
	}

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
