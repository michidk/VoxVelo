package dev.michidk.voxvelo.track;

import dev.michidk.voxvelo.registry.ModTickets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Server-thread tree cleanup with independently owned chunk tickets and no synchronous chunk loads. */
final class TrackTreeRemoval implements AutoCloseable {
	private static final TicketType TICKET = ModTickets.TRACK_TREE_CLEANUP;
	private static final class Pending extends RuntimeException {
		Pending() { super(null, null, false, false); }
	}
	private static final Pending PENDING = new Pending();
	private final ServerLevel level;
	private final TrackTreePlan plan;
	private final Map<ChunkPos, CompletableFuture<?>> tickets = new LinkedHashMap<>();
	private Iterator<TrackTreePlan.Pos> removal;
	private long changed;

	static boolean candidate(BlockState state) {
		return state.is(BlockTags.LOGS) || (state.is(BlockTags.LEAVES)
			&& state.hasProperty(BlockStateProperties.PERSISTENT)
			&& !state.getValue(BlockStateProperties.PERSISTENT));
	}

	TrackTreeRemoval(ServerLevel level, BlockPos seed) {
		this.level = level;
		plan = new TrackTreePlan(new TrackTreePlan.Pos(seed.getX(), seed.getY(), seed.getZ()), this::read);
	}

	private TrackTreePlan.Node read(TrackTreePlan.Pos pos) {
		BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
		if (pos.y() < level.getMinY() || pos.y() > level.getMaxY()) return TrackTreePlan.Node.OTHER;
		if (!level.getWorldBorder().isWithinBounds(block))
			throw new IllegalStateException("Tree cleanup reaches the world border");
		ChunkPos cp = new ChunkPos(pos.x() >> 4, pos.z() >> 4);
		if (!tickets.containsKey(cp)) {
			tickets.put(cp, null);
			tickets.put(cp, level.getChunkSource().addTicketAndLoadWithRadius(TICKET, cp, 0));
		}
		CompletableFuture<?> future = tickets.get(cp);
		if (!future.isDone()) throw PENDING;
		Object result = future.join();
		if (result instanceof ChunkResult<?> loaded && !loaded.isSuccess())
			throw new IllegalStateException("Could not load tree chunk " + cp + ": " + loaded.getError());
		var chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
		if (chunk == null) throw PENDING;
		BlockState state = chunk.getBlockState(block);
		if (state.is(BlockTags.LOGS)) return new TrackTreePlan.Node(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 0);
		if (candidate(state) && state.hasProperty(BlockStateProperties.DISTANCE))
			return new TrackTreePlan.Node(null, state.getValue(BlockStateProperties.DISTANCE));
		return TrackTreePlan.Node.OTHER;
	}

	boolean tick(long deadline) {
		while (System.nanoTime() < deadline) {
			if (removal == null) {
				try { if (plan.step()) removal = plan.removal().iterator(); }
				catch (Pending pending) {
					if (!level.getChunkSource().pollTask()) return false;
				}
				continue;
			}
			if (!removal.hasNext()) return true;
			var pos = removal.next();
			BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
			BlockState state = level.getBlockState(block);
			if (candidate(state)) {
				// Preserve any water contained in leaves rather than creating a dry hole outside the track.
				BlockState replacement = state.getFluidState().createLegacyBlock();
				if (!level.setBlock(block, replacement, Block.UPDATE_ALL))
					throw new IllegalStateException("World refused tree cleanup at " + block.toShortString());
				changed++;
			}
		}
		return false;
	}

	long changed() { return changed; }

	@Override public void close() {
		for (ChunkPos cp : tickets.keySet()) level.getChunkSource().removeTicketWithRadius(TICKET, cp, 0);
		tickets.clear();
	}
}
