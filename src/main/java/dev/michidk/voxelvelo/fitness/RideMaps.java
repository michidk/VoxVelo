package dev.michidk.voxelvelo.fitness;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.network.RideMapPayload;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Turns a recorded ride into a locked filled map: the route as a red line with a green start and a black finish,
 * over the terrain it passed through.
 *
 * <p>The route comes from the rider's client, but the terrain does not: the server remembers which chunks each
 * player rode a bike through this session and only paints terrain within {@value #TERRAIN_RADIUS_CHUNKS} chunks of
 * those, and only from chunks that are loaded right now. Everything else stays blank map paper. So a map request can
 * never reveal parts of the world its sender has not been to, and it never loads or generates chunks.
 *
 * <p>In survival a ride map costs one empty map from the inventory, like any other map; creative players get it free.
 */
final class RideMaps {
	private static final int TRACK_INTERVAL_TICKS = 20;
	private static final int COOLDOWN_TICKS = 200;
	private static final int TERRAIN_RADIUS_CHUNKS = 2;
	/** About 2,000 km of riding; beyond that new chunks are not remembered. */
	private static final int MAX_TRACKED_CHUNKS = 1 << 16;

	private static final byte ROUTE = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
	private static final byte START = MapColor.COLOR_LIGHT_GREEN.getPackedId(MapColor.Brightness.HIGH);
	private static final byte FINISH = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL);
	private static final Style LORE = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);

	private static final Map<UUID, Map<ResourceKey<Level>, LongOpenHashSet>> RIDDEN = new HashMap<>();
	private static final Map<UUID, Integer> LAST_MAP_TICK = new HashMap<>();

	private RideMaps() {
	}

	/** Remembers the chunk under every bike rider, once a second. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % TRACK_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player) {
				LongOpenHashSet chunks = RIDDEN.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
					.computeIfAbsent(bike.level().dimension(), dimension -> new LongOpenHashSet());
				if (chunks.size() < MAX_TRACKED_CHUNKS) {
					chunks.add(ChunkPos.pack(bike.blockPosition()));
				}
			}
		}
	}

	static void forget(UUID player) {
		RIDDEN.remove(player);
		LAST_MAP_TICK.remove(player);
	}

	static void handle(RideMapPayload payload, ServerPlayer player, MinecraftServer server) {
		int now = server.getTickCount();
		Integer last = LAST_MAP_TICK.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			player.sendSystemMessage(Component.translatable("voxelvelo.ride.map_cooldown"));
			return;
		}
		Identifier dimensionId = Identifier.tryParse(payload.dimension());
		ServerLevel level = dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
		if (level == null) {
			player.sendSystemMessage(Component.translatable("voxelvelo.ride.map_failed"));
			return;
		}
		boolean free = player.hasInfiniteMaterials();
		int emptyMapSlot = free ? -1 : findEmptyMap(player);
		if (!free && emptyMapSlot < 0) {
			player.sendSystemMessage(Component.translatable("voxelvelo.ride.map_needs_paper"));
			return;
		}

		ItemStack map;
		try {
			map = create(level, player, payload);
		} catch (RuntimeException e) {
			VoxelVelo.LOGGER.warn("Could not create a ride map for {}", player.getName().getString(), e);
			player.sendSystemMessage(Component.translatable("voxelvelo.ride.map_failed"));
			return;
		}
		if (!free) {
			player.getInventory().getItem(emptyMapSlot).shrink(1);
		}
		if (!player.getInventory().add(map)) {
			player.drop(map, false, Prediction.SERVER_ONLY);
		}
		LAST_MAP_TICK.put(player.getUUID(), now);
		player.sendSystemMessage(Component.translatable("voxelvelo.ride.map_given"));
	}

	private static int findEmptyMap(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(Items.MAP)) {
				return slot;
			}
		}
		return -1;
	}

	private static ItemStack create(ServerLevel level, ServerPlayer player, RideMapPayload payload) {
		List<RideMapPayload.Point> points = payload.points();
		RideMapLayout layout = RideMapLayout.fit(points);
		byte[] colors = new byte[RideMapLayout.SIZE * RideMapLayout.SIZE];
		if (!level.dimensionType().hasCeiling()) {
			paintTerrain(level, layout, colors, RIDDEN.getOrDefault(player.getUUID(), Map.of()).get(level.dimension()));
		}
		layout.drawRoute(colors, points, ROUTE, START, FINISH);

		// Vanilla snaps a new map's centre to its grid; the saved form lets the map sit exactly on the route.
		MapItemSavedData fresh = MapItemSavedData.createFresh(layout.centerX(), layout.centerZ(), (byte) layout.scale(),
			layout.aligned(), false, level.dimension());
		CompoundTag tag = (CompoundTag) MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
		tag.putInt("xCenter", layout.centerX());
		tag.putInt("zCenter", layout.centerZ());
		tag.putBoolean("locked", true);
		tag.putByteArray("colors", colors);
		MapItemSavedData data = MapItemSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		MapId id = level.getFreeMapId();
		level.setMapData(id, data);

		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("voxelvelo.ride.map_name",
			String.format(java.util.Locale.ROOT, "%.2f", payload.distanceM() / 1000.0)));
		List<Component> lore = new java.util.ArrayList<>();
		if (!payload.label().isBlank()) {
			lore.add(Component.literal(payload.label()).withStyle(LORE));
		}
		lore.add(Component.translatable("voxelvelo.ride.map_lore_time", formatDuration(payload.timerSeconds())).withStyle(LORE));
		if (payload.avgPowerWatts() != RideMapPayload.NO_POWER) {
			lore.add(Component.translatable("voxelvelo.ride.map_lore_power", payload.avgPowerWatts()).withStyle(LORE));
		}
		lore.add(Component.translatable("voxelvelo.ride.map_lore_ascent", payload.ascentM()).withStyle(LORE));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static String formatDuration(int seconds) {
		int s = Math.max(0, seconds);
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
	}

	/**
	 * Paints the top block of every pixel whose chunk is loaded and near the ridden chunks, shaded like a vanilla
	 * map: brighter where the ground rises towards the north edge, darker where it falls, water darker with depth.
	 */
	private static void paintTerrain(ServerLevel level, RideMapLayout layout, byte[] colors, LongOpenHashSet ridden) {
		if (ridden == null || ridden.isEmpty()) {
			return;
		}
		Long2BooleanOpenHashMap visible = new Long2BooleanOpenHashMap();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int[] previousRow = new int[RideMapLayout.SIZE];
		for (int pz = 0; pz < RideMapLayout.SIZE; pz++) {
			for (int px = 0; px < RideMapLayout.SIZE; px++) {
				int x = layout.blockX(px);
				int z = layout.blockZ(pz);
				int cx = x >> 4;
				int cz = z >> 4;
				long chunkKey = ChunkPos.pack(cx, cz);
				boolean near = visible.computeIfAbsent(chunkKey, key -> nearRidden(ridden, cx, cz));
				LevelChunk chunk = near ? level.getChunkSource().getChunkNow(cx, cz) : null;
				if (chunk == null) {
					previousRow[px] = Integer.MIN_VALUE;
					continue;
				}
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
				BlockState state;
				MapColor color;
				do {
					y--;
					pos.set(x, y, z);
					state = chunk.getBlockState(pos);
					color = state.getMapColor(level, pos);
				} while (color == MapColor.NONE && y > minY);

				MapColor.Brightness brightness;
				if (!state.getFluidState().isEmpty()) {
					int depth = 0;
					int below = y;
					while (depth < 10 && below > minY && !chunk.getBlockState(pos.set(x, --below, z)).getFluidState().isEmpty()) {
						depth++;
					}
					brightness = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
				} else {
					int north = previousRow[px];
					brightness = north == Integer.MIN_VALUE || north == y ? MapColor.Brightness.NORMAL
						: y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
				}
				previousRow[px] = y;
				colors[px + pz * RideMapLayout.SIZE] = color.getPackedId(brightness);
			}
		}
	}

	private static boolean nearRidden(LongOpenHashSet ridden, int cx, int cz) {
		for (int dx = -TERRAIN_RADIUS_CHUNKS; dx <= TERRAIN_RADIUS_CHUNKS; dx++) {
			for (int dz = -TERRAIN_RADIUS_CHUNKS; dz <= TERRAIN_RADIUS_CHUNKS; dz++) {
				if (ridden.contains(ChunkPos.pack(cx + dx, cz + dz))) {
					return true;
				}
			}
		}
		return false;
	}
}
