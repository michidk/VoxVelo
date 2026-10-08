package dev.michidk.voxvelo.bikes.track;

import dev.michidk.voxvelo.bikes.registry.ModTickets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** A bounded loading window. Every method is called on the server thread; queued builds acquire no tickets. */
final class TrackChunkLoader implements AutoCloseable {
	private static final int LOOKAHEAD = 32;
	/** How long the server thread rests when no loading callback is ready yet; short, so a callback is not left waiting. */
	private static final long IDLE_PARK_NANOS = 100_000;
	private static final TicketType TICKET = ModTickets.TRACK_BUILD;
	private record Pending(ChunkPos pos, CompletableFuture<?> future) {}

	private final ServerChunkCache source;
	private final List<TrackGeometry.ChunkColumns> batches;
	private final Deque<Pending> pending = new ArrayDeque<>();
	private int current;
	private int requested;

	TrackChunkLoader(ServerLevel level, List<TrackGeometry.ChunkColumns> batches) {
		source = level.getChunkSource();
		this.batches = batches;
	}

	/** Run loading callbacks within the build's tick budget, without joining an unfinished future. */
	LevelChunk readyChunk(long deadline) {
		while (pending.size() < LOOKAHEAD && requested < batches.size() && System.nanoTime() < deadline) {
			var batch = batches.get(requested++);
			var pos = new ChunkPos(batch.x(), batch.z());
			// Record ownership before scheduling so close() also cleans up a failed request.
			pending.addLast(new Pending(pos, null));
			var future = source.addTicketAndLoadWithRadius(TICKET, pos, 0);
			pending.removeLast();
			pending.addLast(new Pending(pos, future));
		}
		if (pending.isEmpty()) return null;
		Pending first = pending.getFirst();
		while (System.nanoTime() < deadline) {
			if (first.future.isDone()) {
				Object result = first.future.join();
				if (result instanceof ChunkResult<?> loaded && !loaded.isSuccess())
					throw new IllegalStateException("Could not load track chunk " + first.pos + ": " + loaded.getError());
				LevelChunk chunk = source.getChunkNow(first.pos.x(), first.pos.z());
				if (chunk != null) return chunk;
			}
			// Generation alternates worker jobs with server-thread callbacks. Merely checking once per tick
			// adds a 50 ms delay at each such handoff. Pump ready callbacks, yielding briefly when workers are busy.
			if (!source.pollTask()) LockSupport.parkNanos(IDLE_PARK_NANOS);
		}
		return null;
	}

	List<TrackGeometry.Cell> cells() { return batches.get(current).cells(); }

	void advance() {
		source.removeTicketWithRadius(TICKET, pending.removeFirst().pos, 0);
		current++;
	}

	@Override public void close() {
		for (Pending request : pending) source.removeTicketWithRadius(TICKET, request.pos, 0);
		pending.clear();
	}
}
