package dev.michidk.voxelvelo.bike;

/**
 * The three bike archetypes. Each one is just a set of {@link BikePhysics.Params}; the simulation code
 * is identical for all of them, so tuning a bike never means touching physics code.
 *
 * <ul>
 *   <li><b>Road</b>: light and fast on hard ground, nervous and slow off-road.</li>
 *   <li><b>Gravel</b>: the all-rounder; slightly slower on tarmac, copes well with loose ground.</li>
 *   <li><b>Mountain</b>: heavy and draggy, but grippy, stable and able to roll over blocks.</li>
 * </ul>
 */
public enum BikeType {
	ROAD("road", new BikeRig(20.9, 6.075, 3.2, 19.6, -7.75, 6.3, 2.0, 1.5652, 1.1071, 0.3084), 0.5F,
		new BikePhysics.Params(75.0, 8.5, 26.0, 0.30, 0.98, 2.0, 7.0, 0.4, 1.00, 30.0, 6.0, 8.0, 1.00, 2.30, 0.65)),
	GRAVEL("gravel", new BikeRig(20.9, 6.075, 3.2, 19.6, -7.75, 6.8, 2.0, 1.5652, 1.1071, 0.3084), 0.6F,
		new BikePhysics.Params(75.0, 10.5, 22.0, 0.36, 0.97, 2.2, 8.0, 0.4, 1.05, 35.0, 7.0, 7.0, 1.20, 1.00, 0.95)),
	MOUNTAIN("mountain", new BikeRig(19.9, 6.075, 4.35, 19.85, -6.9, 7.2, 2.0, 1.5652, 1.1071, 0.2954), 1.0F,
		new BikePhysics.Params(75.0, 14.0, 17.0, 0.46, 0.94, 2.5, 8.5, 0.4, 1.12, 40.0, 9.0, 6.0, 1.60, 0.85, 1.10));

	public final String id;
	/** Where the model puts the saddle, grips and cranks. */
	public final BikeRig rig;
	/** How tall a ledge the bike rolls over without stopping, in blocks. */
	public final float stepHeight;
	private final BikePhysics.Params baseParams;

	BikeType(String id, BikeRig rig, float stepHeight, BikePhysics.Params baseParams) {
		this.id = id;
		this.rig = rig;
		this.stepHeight = stepHeight;
		this.baseParams = baseParams;
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
