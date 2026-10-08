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
		for (int y = top; y >= bottom; y--) {
			pos.set(x, y, z);
			BlockState state = this.level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(this.level, pos);
			if (!shape.isEmpty()) {
				return OptionalDouble.of(y + shape.max(Direction.Axis.Y));
			}
		}
		return OptionalDouble.empty();
	}
}
