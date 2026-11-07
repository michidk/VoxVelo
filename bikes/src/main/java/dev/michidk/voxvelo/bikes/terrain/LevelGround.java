package dev.michidk.voxvelo.bikes.terrain;

import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** {@link TerrainSlope.Ground} backed by the blocks of a loaded level. */
public final class LevelGround implements TerrainSlope.Ground {
	/** How far above the previous surface a block still counts as a climbable step, in blocks. */
	private static final int STEP_UP = 2;
	/** How far below the previous surface to look for the ground, in blocks. */
	private static final int DROP_DOWN = 4;

	private final BlockGetter level;

	public LevelGround(BlockGetter level) {
		this.level = level;
	}

	@Override
	public OptionalDouble height(int x, int z, double referenceY) {
		int top = (int) Math.floor(referenceY) + STEP_UP;
		int bottom = (int) Math.floor(referenceY) - DROP_DOWN;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double closest = Double.NaN;
		double distance = Double.POSITIVE_INFINITY;
		for (int y = top; y >= bottom; y--) {
			pos.set(x, y, z);
			BlockState state = this.level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(this.level, pos);
			if (!shape.isEmpty()) {
				// Buried block tops inside a solid hill are not rideable surfaces.
				BlockPos above = pos.above();
				VoxelShape cover = this.level.getBlockState(above).getCollisionShape(this.level, above);
				if (shape.max(Direction.Axis.Y) >= 1 && !cover.isEmpty() && cover.min(Direction.Axis.Y) <= 0) continue;
				double surface = y + shape.max(Direction.Axis.Y);
				double delta = Math.abs(surface - referenceY);
				if (delta < distance) {
					closest = surface;
					distance = delta;
				}
			}
		}
		return Double.isNaN(closest) ? OptionalDouble.empty() : OptionalDouble.of(closest);
	}
}
