package dev.michidk.voxelvelo.track;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Automatic survey and construction. World access stays on server ticks. */
final class TrackBuild {
    private enum Phase { SURVEY, GRADE, PLACE }
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final TrackGeometry.Route route;
    private final BlockState material;
    private final double grade;
    private final Map<TrackGeometry.Cell, Integer> surfaces = new LinkedHashMap<>();
    private final List<TrackGeometry.Cell> surveyCells;
    private Map<TrackGeometry.Cell, Integer> heights;
    private CompletableFuture<Map<TrackGeometry.Cell, Integer>> grading;
    private Phase phase = Phase.SURVEY;
    private int column;
    private int cursorY = Integer.MIN_VALUE;
    private long changed;

    TrackBuild(CommandSourceStack source, TrackGeometry.Route route, BlockState material, double grade) {
        this.source = source;
        this.level = source.getLevel();
        this.route = route;
        this.material = material;
        this.grade = grade;
        // Survey a shoulder too: bordering liquid must not spill onto a lower road column.
        Set<TrackGeometry.Cell> survey = new LinkedHashSet<>(route.road());
        for (TrackGeometry.Cell cell : route.road()) {
            if (!level.getWorldBorder().isWithinBounds(new BlockPos(cell.x(), 0, cell.z())))
                throw new IllegalArgumentException("Track does not fit inside the world border.");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                survey.add(new TrackGeometry.Cell(cell.x() + dx, cell.z() + dz));
        }
        surveyCells = List.copyOf(survey);
    }

    void fail(String reason) {
        source.sendFailure(Component.literal("Track construction interrupted: " + reason
            + ". Already placed blocks are kept (" + changed + ")."));
    }

    boolean tick() {
        long deadline = System.nanoTime() + 5_000_000;
        int budget = phase == Phase.SURVEY ? 16 : 1024;
        while (budget-- > 0 && System.nanoTime() < deadline) {
            if (phase == Phase.SURVEY) {
                if (column < surveyCells.size()) { survey(surveyCells.get(column++)); continue; }
                Map<TrackGeometry.Cell, Integer> snapshot = new LinkedHashMap<>(surfaces);
                grading = CompletableFuture.supplyAsync(() -> TrackGeometry.embankment(snapshot, grade));
                phase = Phase.GRADE;
                column = 0;
                return false;
            }
            if (phase == Phase.GRADE) {
                if (!grading.isDone()) return false;
                heights = grading.join();
                phase = Phase.PLACE;
            }
            if (column == route.road().size()) {
                TrackGeometry.Point start = route.centerline().getFirst();
                TrackGeometry.Cell cell = new TrackGeometry.Cell((int) Math.round(start.x()), (int) Math.round(start.z()));
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Track complete: %.3f km. Start: %d %d %d.", route.length() / 1000,
                    cell.x(), heights.get(cell) + 1, cell.z())), true);
                return true;
            }
            TrackGeometry.Cell cell = route.road().get(column);
            int roadY = heights.get(cell);
            if (cursorY == Integer.MIN_VALUE) {
                // Extend foundations through liquids and caves to solid ground, without a depth rejection.
                int bottom = Math.min(surfaces.get(cell), roadY);
                while (bottom > level.getMinY() + 1) {
                    BlockPos below = new BlockPos(cell.x(), bottom - 1, cell.z());
                    BlockState support = level.getBlockState(below);
                    if (support.getFluidState().isEmpty() && support.isCollisionShapeFullBlock(level, below)) break;
                    bottom--;
                }
                cursorY = bottom;
            }
            BlockPos pos = new BlockPos(cell.x(), cursorY, cell.z());
            BlockState target = cursorY <= roadY ? material : Blocks.AIR.defaultBlockState();
            BlockState existing = level.getBlockState(pos);
            if (!existing.equals(target)) {
                if (!level.setBlock(pos, target, Block.UPDATE_ALL))
                    throw new IllegalStateException("World refused placement at " + pos.toShortString());
                changed++;
            }
            if (++cursorY > roadY + 5) { cursorY = Integer.MIN_VALUE; column++; }
        }
        return false;
    }

    private void survey(TrackGeometry.Cell cell) {
        int top = level.getChunk(cell.x() >> 4, cell.z() >> 4)
            .getHeight(Heightmap.Types.WORLD_SURFACE, cell.x() & 15, cell.z() & 15);
        // In the Nether, project downward from the invocation height rather than onto its roof.
        if (level.dimensionType().hasCeiling()) top = Math.min(top, (int) Math.floor(source.getPosition().y));
        int surface = Math.clamp((int) Math.floor(source.getPosition().y) - 1, level.getMinY() + 1, level.getMaxY() - 5);
        for (int y = Math.min(top, level.getMaxY()); y > level.getMinY(); y--) {
            BlockPos pos = new BlockPos(cell.x(), y, cell.z());
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) continue;
            if (!state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos) || state.hasBlockEntity()) {
                surface = y;
                // Lay road above containers and unbreakable supports.
                if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) surface++;
                break;
            }
        }
        surfaces.put(cell, Math.clamp(surface, level.getMinY() + 1, level.getMaxY() - 5));
    }
}
