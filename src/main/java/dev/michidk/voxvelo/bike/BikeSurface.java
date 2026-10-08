package dev.michidk.voxvelo.bike;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central terrain abstraction. Everything the physics, tire wear and trainer resistance need to know
 * about a surface lives here instead of being scattered as block checks.
 */
public enum BikeSurface {
	//    rolling Crr, traction, looseness (0 = hard/paved, 1 = fully loose)
	PATH(0.0030, 1.00, 0.25),
	STONE(0.0045, 1.00, 0.0),
	GRASS(0.0090, 0.90, 0.7),
	DIRT(0.0120, 0.85, 0.8),
	SAND(0.0300, 0.70, 1.0),
	SNOW(0.0200, 0.60, 1.0),
	ICE(0.0020, 0.25, 0.0),
	WATER(0.1500, 0.30, 1.0);

	/** Base rolling resistance coefficient (dimensionless, Crr) for a neutral tyre. */
	public final double rollingResistance;
	/** Fraction of the normal drive and brake force that can be transferred to the surface. */
	public final double traction;
	/** How loose the surface is; each bike type blends between its paved and loose tyre behaviour by this. */
	public final double looseness;

	BikeSurface(double rollingResistance, double traction, double looseness) {
		this.rollingResistance = rollingResistance;
		this.traction = traction;
		this.looseness = looseness;
	}

	/** The surface with this ordinal, or {@link #PATH} for anything out of range (synced or reported values). */
	public static BikeSurface byOrdinal(int ordinal) {
		BikeSurface[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PATH;
	}

	public static BikeSurface classify(BlockState state, boolean inWater) {
		if (inWater) {
			return WATER;
		}
		if (state.is(Blocks.DIRT_PATH)) {
			return PATH;
		}
		if (state.is(BlockTags.ICE)) {
			return ICE;
		}
		if (state.is(BlockTags.SAND)) {
			return SAND;
		}
		if (state.is(BlockTags.SNOW)) {
			return SNOW;
		}
		if (state.is(Blocks.GRASS_BLOCK)) {
			return GRASS;
		}
		if (state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL)) {
			return DIRT;
		}
		return STONE;
	}
}
