package dev.michidk.voxvelo.fitness;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * What one rider's surroundings looked like from above, kept for one dimension so a ride map can be painted after
 * the chunks have unloaded.
 *
 * <p>While someone rides, the server samples the chunks around the bike that are loaded right now, which are the ones
 * that rider's client was sent anyway, so a map never shows anything the rider could not already see. Each chunk keeps
 * an 8 x 8 grid, one sample per 2 x 2 blocks: the top block's map colour and its height, or for water a brightness by
 * depth. That is 256 bytes a chunk, so {@link #MAX_CHUNKS} chunks are about 4 MB.
 */
final class RideTerrainSketch {
	static final int MAX_CHUNKS = 16_384;
	private static final int GRID = 8;
	private static final int CELLS = GRID * GRID;
	/** Marks a land cell in the brightness byte; water stores its brightness ordinal instead. */
	private static final byte LAND = -1;

	/** Per chunk: colour ids, water brightness (or {@link #LAND}), then heights as two bytes each. */
	private final Long2ObjectOpenHashMap<byte[]> chunks = new Long2ObjectOpenHashMap<>();

	int size() {
		return this.chunks.size();
	}

	boolean isFull() {
		return this.chunks.size() >= MAX_CHUNKS;
	}

	/**
	 * Samples up to {@code budget} loaded chunks within {@code radius} chunks of {@code centre} that are not sketched
	 * yet, nearest first. Returns how many were added.
	 */
	int captureAround(ServerLevel level, BlockPos centre, int radius, int budget) {
		int cx = centre.getX() >> 4;
		int cz = centre.getZ() >> 4;
		int added = 0;
		for (int ring = 0; ring <= radius && added < budget && !this.isFull(); ring++) {
			for (int dx = -ring; dx <= ring && added < budget && !this.isFull(); dx++) {
				for (int dz = -ring; dz <= ring && added < budget && !this.isFull(); dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					long key = ChunkPos.pack(cx + dx, cz + dz);
					if (this.chunks.containsKey(key)) {
						continue;
					}
					LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
					if (chunk != null) {
						this.chunks.put(key, sample(level, chunk));
						added++;
					}
				}
			}
		}
		return added;
	}

	/** Samples a loaded chunk into the sketch (replacing an older sample). */
	void capture(ServerLevel level, LevelChunk chunk) {
		if (!this.isFull()) {
			this.chunks.put(chunk.getPos().pack(), sample(level, chunk));
		}
	}

	private static byte[] sample(ServerLevel level, LevelChunk chunk) {
		byte[] data = new byte[CELLS * 4];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int gz = 0; gz < GRID; gz++) {
			for (int gx = 0; gx < GRID; gx++) {
				int x = baseX + gx * 2;
				int z = baseZ + gz * 2;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
				BlockState state;
				MapColor color;
				do {
					y--;
					pos.set(x, y, z);
					state = chunk.getBlockState(pos);
					color = state.getMapColor(level, pos);
				} while (color == MapColor.NONE && y > minY);
				byte brightness = LAND;
				if (!state.getFluidState().isEmpty()) {
					int depth = 0;
					int below = y;
					while (depth < 10 && below > minY && !chunk.getBlockState(pos.set(x, --below, z)).getFluidState().isEmpty()) {
						depth++;
					}
					brightness = (byte) (depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW).ordinal();
				}
				int cell = gx + gz * GRID;
				data[cell] = (byte) color.id;
				data[CELLS + cell] = brightness;
				data[2 * CELLS + cell * 2] = (byte) (y >> 8);
				data[2 * CELLS + cell * 2 + 1] = (byte) y;
			}
		}
		return data;
	}

	/** The sketched cell holding block x/z, or null when that chunk was never seen. */
	@Nullable Cell cell(int x, int z) {
		byte[] data = this.chunks.get(ChunkPos.pack(x >> 4, z >> 4));
		if (data == null) {
			return null;
		}
		int cell = ((x & 15) >> 1) + ((z & 15) >> 1) * GRID;
		int height = (short) ((data[2 * CELLS + cell * 2] << 8) | (data[2 * CELLS + cell * 2 + 1] & 0xFF));
		return new Cell(data[cell] & 0xFF, data[CELLS + cell], height);
	}

	/**
	 * @param colorId    the {@link MapColor} id, 0 for nothing
	 * @param brightness the water brightness ordinal, or negative for land (shaded by height when painting)
	 */
	record Cell(int colorId, int brightness, int height) {
		boolean isWater() {
			return this.brightness >= 0;
		}
	}
}
