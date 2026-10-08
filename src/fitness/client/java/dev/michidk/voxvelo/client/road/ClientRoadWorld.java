package dev.michidk.voxvelo.client.road;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** {@link RoadWorld} over the loaded blocks of a level and the player's road block list. */
public final class ClientRoadWorld implements RoadWorld {
	private final BlockGetter level;
	private final RoadBlocks roadBlocks;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

	public ClientRoadWorld(BlockGetter level, RoadBlocks roadBlocks) {
		this.level = level;
		this.roadBlocks = roadBlocks;
	}

	@Override
	public boolean isRoad(int x, int y, int z) {
		return this.roadBlocks.matches(this.level.getBlockState(this.pos.set(x, y, z)));
	}

	@Override
	public boolean isPassable(int x, int y, int z) {
		this.pos.set(x, y, z);
		BlockState state = this.level.getBlockState(this.pos);
		return state.getCollisionShape(this.level, this.pos).isEmpty() && state.getFluidState().isEmpty();
	}
}
