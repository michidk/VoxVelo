package dev.michidk.voxelvelo.client.terrain;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Measures the slope around the bike the local player is riding, over a window of blocks in each direction,
 * and keeps the latest value for the trainer feedback and the HUD. The measurement is a handful of block
 * lookups, so it runs every few ticks rather than every frame.
 */
public final class RideTerrain {
	private static final int INTERVAL_TICKS = 4;

	private final VoxelVeloConfig config;
	private int ticks;
	private OptionalDouble slope = OptionalDouble.empty();

	public RideTerrain(VoxelVeloConfig config) {
		this.config = config;
	}

	/** Rise over run along the heading (0.05 = 5 %), or empty when not riding or there is no usable ground. */
	public OptionalDouble slope() {
		return this.slope;
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || !(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
			this.slope = OptionalDouble.empty();
			this.ticks = 0;
			return;
		}
		if (this.ticks++ % INTERVAL_TICKS != 0) {
			return;
		}
		double yaw = Math.toRadians(bike.getYRot());
		this.slope = TerrainSlope.measure(
			new LevelGround(client.level),
			bike.getX(), bike.getZ(),
			-Math.sin(yaw), Math.cos(yaw),
			bike.getY(),
			this.config.slopeWindowBlocks
		);
	}
}
