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
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Reads and writes the world only on server ticks; numerical grading runs on a detached terrain snapshot. */
final class TrackBuild {
    private enum Phase { SURVEY, GRADE, CHECK, PLACE }
    private final CommandSourceStack source;
    private final ServerLevel level;
    private final TrackGeometry.Route route;
    private final BlockState material;
    private final double grade;
    private final Map<TrackGeometry.Cell, Integer> ground = new LinkedHashMap<>();
    private final Map<TrackGeometry.Cell, Integer> foundations = new HashMap<>();
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
    }

    String status() { return "Track: " + phase + ", " + column + "/" + route.road().size() + " columns; " + changed + " blocks changed."; }
    void fail(String reason) { source.sendFailure(Component.literal("Track stopped: " + reason + ". Already placed blocks are kept (" + changed + ").")); }

    boolean tick() {
        long deadline = System.nanoTime() + 5_000_000;
        int budget = phase == Phase.SURVEY ? 16 : 1024;
        while (budget-- > 0 && System.nanoTime() < deadline) {
            if (phase == Phase.GRADE) {
                if (!grading.isDone()) return false;
                heights = grading.join();
                for (TrackGeometry.Cell cell : route.road()) {
                    int y = heights.get(cell);
                    if (Math.abs(y - ground.get(cell)) > 32) throw new IllegalArgumentException("terrain needs more than 32 blocks of cut/fill; choose flatter terrain or a higher grade");
                    if (y < level.getMinY() + 1 || y + 5 > level.getMaxY()) throw new IllegalArgumentException("insufficient world height for the road and clearance");
                }
                phase = Phase.CHECK;
            }
            if (column == route.road().size()) {
                column = 0;
                if (phase == Phase.SURVEY) {
                    Map<TrackGeometry.Cell, Integer> snapshot = new LinkedHashMap<>(ground);
                    grading = CompletableFuture.supplyAsync(() -> TrackGeometry.terrace(snapshot, grade));
                    phase = Phase.GRADE;
                    return false;
                }
                if (phase == Phase.CHECK) {
                    phase = Phase.PLACE;
                    source.sendSuccess(() -> Component.literal("Terrain checks passed. Building road and five-block air clearance."), true);
                } else {
                    TrackGeometry.Point start = route.centerline().getFirst();
                    TrackGeometry.Cell startCell = new TrackGeometry.Cell((int) Math.round(start.x()), (int) Math.round(start.z()));
                    source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Track complete: %.3f km horizontal centerline, %d road columns. Start: %d %d %d. Grade %.1f%% before block rounding.",
                        route.length() / 1000, route.road().size(), startCell.x(),
                        heights.get(startCell) + 1, startCell.z(), grade * 100)), true);
                    return true;
                }
            }
            TrackGeometry.Cell cell = route.road().get(column);
            if (phase == Phase.SURVEY) {
                survey(cell);
                column++;
                continue;
            }
            int roadY = heights.get(cell);
            int top = Math.max(ground.get(cell), roadY) + 5;
            if (cursorY == Integer.MIN_VALUE) {
                if (phase == Phase.CHECK) {
                    int bottom = Math.min(ground.get(cell), roadY);
                    // A cut can expose a cave: extend the foundation until it actually meets solid ground.
                    while (bottom > level.getMinY() + 1) {
                        BlockPos below = new BlockPos(cell.x(), bottom - 1, cell.z());
                        BlockState support = level.getBlockState(below);
                        if (!support.getFluidState().isEmpty()) throw new IllegalArgumentException("liquid beneath road foundation at " + below.toShortString());
                        if (support.isCollisionShapeFullBlock(level, below)) break;
                        if (roadY - bottom >= 32) throw new IllegalArgumentException("road foundation needs more than 32 blocks of fill");
                        bottom--;
                    }
                    foundations.put(cell, bottom);
                }
                cursorY = foundations.get(cell);
            }
            BlockPos pos = new BlockPos(cell.x(), cursorY, cell.z());
            BlockState existing = level.getBlockState(pos);
            validate(pos, existing);
            // Prevent neighboring liquids or falling material from refilling the cleared corridor.
            if (cursorY > roadY) {
                for (int[] direction : DIRECTIONS) {
                    TrackGeometry.Cell neighbor = new TrackGeometry.Cell(cell.x() + direction[0], cell.z() + direction[1]);
                    if (heights.containsKey(neighbor)) continue;
                    BlockState side = level.getBlockState(pos.offset(direction[0], 0, direction[1]));
                    if (!side.getFluidState().isEmpty() || side.getBlock() instanceof FallingBlock)
                        throw new IllegalArgumentException("water, lava or falling terrain borders the clearance at " + pos.toShortString());
                }
            }
            if (phase == Phase.PLACE) {
                BlockState target = cursorY <= roadY ? material : Blocks.AIR.defaultBlockState();
                if (!existing.equals(target)) {
                    if (!level.setBlock(pos, target, Block.UPDATE_ALL)) throw new IllegalStateException("could not place block at " + pos.toShortString());
                    changed++;
                }
            }
            if (++cursorY > top) { cursorY = Integer.MIN_VALUE; column++; }
        }
        return false;
    }

    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private void survey(TrackGeometry.Cell cell) {
        BlockPos pos = new BlockPos(cell.x(), 0, cell.z());
        if (!level.getWorldBorder().isWithinBounds(pos)) throw new IllegalArgumentException("track extends beyond the world border");
        // Level.getHeight returns the world minimum for unloaded chunks; explicitly load the sampled chunk.
        int top = Math.min(level.getMaxY(), level.getChunk(cell.x() >> 4, cell.z() >> 4)
            .getHeight(Heightmap.Types.WORLD_SURFACE, cell.x() & 15, cell.z() & 15));
        for (int y = top; y > level.getMinY(); y--) {
            pos = new BlockPos(cell.x(), y, cell.z());
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) throw new IllegalArgumentException("track crosses water/lava at " + pos.toShortString() + "; choose a land route");
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) continue;
            // Natural ground whitelist: tree trunks, leaves, vegetation and structures are never the surface.
            if (state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.END_STONE) || state.equals(material)) {
                ground.put(cell, y);
                return;
            }
        }
        throw new IllegalArgumentException("no natural ground beneath " + cell.x() + ", " + cell.z());
    }

    private void validate(BlockPos pos, BlockState state) {
        if (!level.isInsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
            throw new IllegalArgumentException("track exceeds world bounds");
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || !state.getFluidState().isEmpty())
            throw new IllegalArgumentException("protected block, block entity or liquid at " + pos.toShortString());
    }
}
