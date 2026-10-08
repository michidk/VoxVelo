package dev.michidk.voxvelo.track;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.michidk.voxvelo.VoxVelo;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Automatic survey and construction. World access stays on server ticks. */
final class TrackBuild {
	/** Blocks cleared above the deck; the last of them is the protective layer. */
	private static final int HEADROOM = 5;
	/** Server time a build may take per tick; chunks still loading make one column cost far more than another. */
	private static final long TICK_BUDGET_NANOS = 10_000_000;
	/** A player within this many blocks of the start when the track is done is put on it. */
	private static final int PUT_ON_TRACK_RADIUS = 24;
	private static final SimpleCommandExceptionType OUTSIDE_BORDER =
		new SimpleCommandExceptionType(Component.translatable("commands.voxvelo.track.outside_border"));
	private static final SimpleCommandExceptionType SHOULDER_OUTSIDE_BORDER =
		new SimpleCommandExceptionType(Component.translatable("commands.voxvelo.track.shoulder_outside_border"));
	private enum Phase { SURVEY, GRADE, PLACE }
	private final CommandSourceStack source;
	private final ServerLevel level;
	private final TrackGeometry.Route route;
	private final BlockState material;
	private final BlockState slab;
	private final double grade;
	private final Map<TrackGeometry.Cell, Integer> surfaces = new LinkedHashMap<>();
	private final Set<TrackGeometry.Cell> liquid = new HashSet<>();
	private final Set<TrackGeometry.Cell> voids = new HashSet<>();
	private final List<TrackGeometry.Cell> surveyCells;
	private TrackChunkLoader chunks;
	private int batchColumn;
	private LevelChunk chunk;
	private Map<TrackGeometry.Cell, TrackPlan.Column> plan;
	private CompletableFuture<Map<TrackGeometry.Cell, TrackPlan.Column>> grading;
	private Phase phase = Phase.SURVEY;
	private int column;
	private int cursorY = Integer.MIN_VALUE;
	private long changed;
	private TrackTreeRemoval tree;
	private BlockPos checkedTree;
	private long phaseAt;
	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("commands.voxvelo.track.bar"),
		BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
	private int shownPercent = -1;

	private void lap(String what) {
		long now = System.nanoTime();
		VoxVelo.LOGGER.debug("Track {} took {} ms ({} road columns)", what, (now - phaseAt) / 1_000_000, route.road().size());
		phaseAt = now;
	}

	/**
	 * Shows survey, grading and placement as one bar: surveying is most of the work, then grading, then placing.
	 * {@code stage} names the stage in the bar, see the commands.voxvelo.track.stage keys.
	 */
	private void progress(String stage, double done) {
		double total = switch (phase) {
			case SURVEY -> 0.7 * done;
			case GRADE -> 0.7 + 0.05 * done;
			case PLACE -> 0.75 + 0.25 * done;
		};
		int percent = (int) Math.round(total * 100);
		if (percent == shownPercent) return;
		shownPercent = percent;
		bar.setName(Component.translatable("commands.voxvelo.track.bar.progress",
			Component.translatable("commands.voxvelo.track.stage." + stage), percent));
		bar.setProgress((float) total);
	}

	/** How far the road ends up from the terrain: the tallest fill and deepest cut over the road columns. */
	private void logDeviation() {
		int fill = 0, cut = 0;
		for (TrackGeometry.Cell cell : route.road()) {
			int d = (int) Math.round(plan.get(cell).ridingY() - (surfaces.get(cell) + 1));
			fill = Math.max(fill, d);
			cut = Math.max(cut, -d);
		}
		VoxVelo.LOGGER.debug("Track follows the terrain within {} blocks of fill and {} of cut", fill, cut);
	}

	private void finish() {
		if (tree != null) { tree.close(); tree = null; }
		chunks.close();
		chunk = null;
		bar.removeAllPlayers();
	}

	void cancel() {
		finish();
		if (grading != null) grading.cancel(false);
	}

	Component status() {
		Component stage = switch (phase) {
			case SURVEY -> Component.translatable("commands.voxvelo.track.status.surveying", column, surveyCells.size());
			case GRADE -> Component.translatable("commands.voxvelo.track.status.grading");
			case PLACE -> Component.translatable("commands.voxvelo.track.status.building", column, plan.size());
		};
		long blocks = changed + (tree == null ? 0 : tree.changed());
		return Component.translatable("commands.voxvelo.track.status", stage, Math.max(0, shownPercent), blocks);
	}

	TrackBuild(CommandSourceStack source, TrackGeometry.Route route, BlockState material, double grade) throws CommandSyntaxException {
		this.source = source;
		this.level = source.getLevel();
		this.route = route;
		this.material = material;
		this.slab = TrackPalette.slabFor(material);
		this.grade = grade;
		// Survey a shoulder too: bordering liquid must not spill onto a lower road column.
		Set<TrackGeometry.Cell> survey = new LinkedHashSet<>(route.road());
		for (TrackGeometry.Cell cell : route.road()) {
			if (!level.getWorldBorder().isWithinBounds(new BlockPos(cell.x(), 0, cell.z())))
				throw OUTSIDE_BORDER.create();
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
				survey.add(new TrackGeometry.Cell(cell.x() + dx, cell.z() + dz));
		}
		for (TrackGeometry.Cell cell : survey) {
			if (!level.getWorldBorder().isWithinBounds(new BlockPos(cell.x(), 0, cell.z())))
				throw SHOULDER_OUTSIDE_BORDER.create();
		}
		surveyCells = List.copyOf(survey);
		chunks = new TrackChunkLoader(level, TrackGeometry.byChunk(surveyCells));
		if (source.getEntity() instanceof ServerPlayer player) bar.addPlayer(player);
	}

	void fail(String reason) {
		finish();
		source.sendFailure(Component.translatable("commands.voxvelo.track.interrupted", reason, changed));
	}

	boolean tick() {
		if (phaseAt == 0) phaseAt = System.nanoTime();
		// The work is bounded by time, not by a count.
		long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
		while (System.nanoTime() < deadline) {
			if (tree != null) {
				if (!tree.tick(deadline)) return false;
				changed += tree.changed();
				tree.close();
				tree = null;
			}
			if (phase == Phase.SURVEY) {
				if (column < surveyCells.size()) {
					if (chunk == null) chunk = chunks.readyChunk(deadline);
					if (chunk == null) return false;
					survey(chunks.cells().get(batchColumn++));
					column++;
					if (batchColumn == chunks.cells().size()) nextChunk();
					if ((column & 63) == 0) progress("surveying", column / (double) surveyCells.size());
					continue;
				}
				Map<TrackGeometry.Cell, Integer> snapshot = new LinkedHashMap<>(surfaces);
				lap("survey");
				Set<TrackGeometry.Cell> wet = Set.copyOf(liquid);
				TrackGeometry.Point first = route.centerline().getFirst();
				TrackGeometry.Cell startCell = new TrackGeometry.Cell((int) Math.round(first.x()), (int) Math.round(first.z()));
				Set<TrackGeometry.Cell> empty = Set.copyOf(voids);
				grading = CompletableFuture.supplyAsync(() -> TrackPlan.create(route.road(), snapshot, wet, empty, grade, startCell));
				phase = Phase.GRADE;
				progress("grading", 0);
				column = 0;
				return false;
			}
			if (phase == Phase.GRADE) {
				if (!grading.isDone()) return false;
				lap("grading");
				plan = grading.join();
				logDeviation();
				chunks.close();
				chunks = new TrackChunkLoader(level, TrackGeometry.byChunk(plan.keySet()));
				phase = Phase.PLACE;
			}
			if (phase == Phase.PLACE && (column & 15) == 0) progress("building", column / (double) plan.size());
			if (column == plan.size()) {
				lap("placement");
				finish();
				TrackGeometry.Point start = route.centerline().getFirst();
				TrackGeometry.Cell cell = new TrackGeometry.Cell((int) Math.round(start.x()), (int) Math.round(start.z()));
				double startY = plan.get(cell).ridingY();
				source.sendSuccess(() -> Component.translatable("commands.voxvelo.track.complete",
					String.format(Locale.ROOT, "%.3f", route.length() / 1000), cell.x(), String.format(Locale.ROOT, "%.1f", startY),
					cell.z()), true);
				putPlayerOnTrack(cell, startY);
				return true;
			}
			if (chunk == null) chunk = chunks.readyChunk(deadline);
			if (chunk == null) return false;
			TrackGeometry.Cell cell = chunks.cells().get(batchColumn);
			TrackPlan.Column section = plan.get(cell);
			int roadY = section.deckY();
			if (cursorY == Integer.MIN_VALUE) {
				// Extend foundations through liquids and caves to solid ground, without a depth rejection.
				boolean suspended = section.kind() == TrackPlan.Kind.BRIDGE && !section.pier();
				int bottom = suspended ? roadY - 1 : Math.min(surfaces.get(cell), roadY);
				while (!suspended && bottom > level.getMinY() + 1) {
					BlockPos below = new BlockPos(cell.x(), bottom - 1, cell.z());
					BlockState support = chunk.getBlockState(below);
					if (support.getFluidState().isEmpty() && support.isCollisionShapeFullBlock(level, below)) break;
					bottom--;
				}
				cursorY = bottom;
			}
			BlockPos pos = new BlockPos(cell.x(), cursorY, cell.z());
			BlockState target = target(section, cursorY);
			BlockState existing = chunk.getBlockState(pos);
			if (!existing.equals(target) && !pos.equals(checkedTree) && TrackTreeRemoval.candidate(existing)) {
				checkedTree = pos;
				tree = new TrackTreeRemoval(level, pos);
				continue;
			}
			if (!existing.equals(target)) {
				if (!level.setBlock(pos, target, Block.UPDATE_ALL))
					throw new IllegalStateException("World refused placement at " + pos.toShortString());
				changed++;
			}
			if (++cursorY > (section.kind() == TrackPlan.Kind.TUNNEL ? section.roofY() : roadY + HEADROOM)) {
				cursorY = Integer.MIN_VALUE;
				column++;
				if (++batchColumn == chunks.cells().size()) nextChunk();
			}
		}
		return false;
	}

	private BlockState target(TrackPlan.Column section, int y) {
		int deck = section.deckY();
		if (section.border()) {
			if (y <= deck) {
				if (y == deck && section.light()) return Blocks.GLOWSTONE.defaultBlockState();
				return Blocks.STONE_BRICKS.defaultBlockState();
			}
			if (section.kind() == TrackPlan.Kind.TUNNEL) return Blocks.STONE_BRICKS.defaultBlockState();
		} else {
			if (y < deck) return material;
			if (y == deck) return section.slab() ? slab : material;
			if (y == section.roofY() && section.kind() == TrackPlan.Kind.TUNNEL)
				return (section.light() ? Blocks.GLOWSTONE : Blocks.STONE_BRICKS).defaultBlockState();
		}
		// The headroom itself stays plain air, so riders passing through never see block outlines. Only the topmost
		// layer of an open stretch is structure void: collision-free, but unlike air it cannot be washed away, so water
		// and lava above the track do not run down into it. Tunnels have their stone roof as that layer.
		if (section.kind() != TrackPlan.Kind.TUNNEL && y == deck + HEADROOM) return Blocks.STRUCTURE_VOID.defaultBlockState();
		return Blocks.AIR.defaultBlockState();
	}

	private void nextChunk() {
		chunks.advance();
		batchColumn = 0;
		chunk = null;
	}

	/** The track starts where it was ordered, so a player still around there ends up standing on it, not beside or inside the road bed. */
	private void putPlayerOnTrack(TrackGeometry.Cell start, double y) {
		if (!(source.getEntity() instanceof ServerPlayer player) || player.level() != level) return;
		double away = Math.hypot(player.getX() - (start.x() + 0.5), player.getZ() - (start.z() + 0.5));
		if (away > PUT_ON_TRACK_RADIUS || player.isPassenger()) return;
		player.teleportTo(start.x() + 0.5, y, start.z() + 0.5);
	}

	private void survey(TrackGeometry.Cell cell) {
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, cell.x() & 15, cell.z() & 15);
		// In the Nether, project downward from the invocation height rather than onto its roof.
		if (level.dimensionType().hasCeiling()) top = Math.min(top, (int) Math.floor(source.getPosition().y));
		int surface = Math.clamp((int) Math.floor(source.getPosition().y) - 1, level.getMinY() + 1, level.getMaxY() - 6);
		boolean found = false;
		for (int y = Math.min(top, level.getMaxY()); y > level.getMinY(); y--) {
			BlockPos pos = new BlockPos(cell.x(), y, cell.z());
			BlockState state = chunk.getBlockState(pos);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) continue;
			if (!state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos) || state.hasBlockEntity()) {
				surface = y;
				found = true;
				if (!state.getFluidState().isEmpty()) liquid.add(cell);
				// Lay road above containers and unbreakable supports.
				if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) surface++;
				break;
			}
		}
		if (!found) voids.add(cell);
		surfaces.put(cell, Math.clamp(surface, level.getMinY() + 1, level.getMaxY() - 6));
	}
}
