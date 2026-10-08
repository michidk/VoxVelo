package dev.michidk.voxvelo.fitness.client.terrain;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.terrain.LevelGround;
import dev.michidk.voxvelo.bikes.terrain.TerrainSlope;
import dev.michidk.voxvelo.fitness.client.road.Heading;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;

/**
 * Measures the slope around the bike the local player is riding, over a window of blocks in each direction,
 * and keeps the latest value for the trainer feedback and the HUD. The measurement is a handful of block
 * lookups, so it runs every few ticks rather than every frame.
 */
public final class RideTerrain {
	/** The measurement is a few dozen block lookups, so every other tick (10 times a second) is cheap and keeps the trainer current. */
	private static final int INTERVAL_TICKS = 2;

	private final FitnessConfig config;
	private int ticks;
	private OptionalDouble slope = OptionalDouble.empty();

	public RideTerrain(FitnessConfig config) {
		this.config = config;
	}

	/** Rise over run along the heading (0.05 = 5 %), or empty when not riding or there is no usable ground. */
	public OptionalDouble slope() {
		return this.slope;
	}

	public void tick(Minecraft client) {
		BikeEntity bike = BikeEntity.riddenBy(client.player);
		if (bike == null || client.level == null) {
			this.slope = OptionalDouble.empty();
			this.ticks = 0;
			return;
		}
		if (this.ticks++ % INTERVAL_TICKS != 0) {
			return;
		}
		Heading heading = Heading.ofYaw(bike.getYRot());
		this.slope = TerrainSlope.measure(
			new LevelGround(client.level),
			bike.getX(), bike.getZ(),
			heading.fx(), heading.fz(),
			bike.getY(),
			this.config.slopeWindowBlocks
		);
	}
}
