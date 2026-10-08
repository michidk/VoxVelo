package dev.michidk.voxvelo.bikes.bike;

/**
 * The three bike archetypes. Each one is just a set of {@link BikePhysics.Params}; the simulation code
 * is identical for all of them, so tuning a bike never means touching physics code.
 *
 * <ul>
 *   <li><b>Road</b>: light and fast on hard ground, nervous and slow off-road; stiff, almost no suspension.</li>
 *   <li><b>Gravel</b>: the all-rounder; slightly slower on tarmac, copes well with loose ground; some give.</li>
 *   <li><b>Mountain</b>: heavy and draggy, but grippy, stable and soaks up bumps and landings with long travel.</li>
 * </ul>
 */
public enum BikeType {
	ROAD("road", new BikeRig(17.556, 5.103, 2.688, 16.464, -6.51, 5.292, 1.68, 1.3148, 1.1071, 0.3084, 14.616, -4.788), 0.15F, 60.0F,
		new BikePhysics.Params(75.0, 8.5, 26.0, 0.30, 0.98, 2.0, 7.0, 0.4, 1.00, 30.0, 6.0, 8.0, 1.00, 2.30, 0.65)),
	GRAVEL("gravel", new BikeRig(17.556, 5.103, 2.688, 16.464, -6.51, 5.712, 1.68, 1.3148, 1.1071, 0.3084, 14.616, -4.788), 0.45F, 80.0F,
		new BikePhysics.Params(75.0, 10.5, 22.0, 0.36, 0.97, 2.2, 8.0, 0.4, 1.05, 35.0, 7.0, 7.0, 1.20, 1.00, 0.95)),
	MOUNTAIN("mountain", new BikeRig(16.716, 5.103, 3.654, 16.674, -5.796, 6.048, 1.68, 1.3148, 1.1071, 0.2954, 15.624, -4.788), 0.90F, 100.0F,
		new BikePhysics.Params(75.0, 14.0, 17.0, 0.46, 0.94, 2.5, 8.5, 0.4, 1.12, 40.0, 9.0, 6.0, 1.60, 0.85, 1.10));

	public final String id;
	/** Where the model puts the saddle, grips and cranks. */
	public final BikeRig rig;
	/**
	 * Suspension travel and softness, 0 (rigid) to 1 (long travel). More suspension means softer landings, smaller
	 * speed loss on bumps, and a springier, bouncier ride.
	 */
	public final float suspension;
	/** Whole-bike durability. Mountain and gravel frames survive more crash and fall damage than road frames. */
	public final float maxDurability;
	private final BikePhysics.Params baseParams;

	BikeType(String id, BikeRig rig, float suspension, float maxDurability, BikePhysics.Params baseParams) {
		this.id = id;
		this.rig = rig;
		this.suspension = suspension;
		this.maxDurability = maxDurability;
		this.baseParams = baseParams;
	}

	/** Share of the fall damage the rider still takes: a bike always softens a landing, suspension softens it more. */
	public float fallDamageMultiplier() {
		return 0.8F - 0.55F * this.suspension;
	}

	/** Extra blocks of a fall the suspension swallows before the rider notices. */
	public float fallAbsorbBlocks() {
		return 1.5F * this.suspension;
	}

	/** Share of the speed kept when rolling up a ledge taller than a slab: stiff bikes feel every block. */
	public double bumpSpeedFactor() {
		return 0.75 + 0.2 * this.suspension;
	}

	public BikePhysics.Params params() {
		return this.baseParams;
	}

	public static BikeType byId(String id) {
		for (BikeType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return GRAVEL;
	}

	public static BikeType byOrdinal(int ordinal) {
		BikeType[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : GRAVEL;
	}
}
