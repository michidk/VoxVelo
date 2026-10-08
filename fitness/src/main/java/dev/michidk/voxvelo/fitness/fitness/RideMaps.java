package dev.michidk.voxvelo.fitness.fitness;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.fitness.FitnessMod;
import dev.michidk.voxvelo.fitness.network.RideMapPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * Turns a recorded ride into a locked filled map: the route as a red line with a green start and a black finish,
 * over the terrain it passed through.
 *
 * <p>The route comes from the rider's client, but the terrain does not: while a player rides, the server sketches the
 * loaded chunks around the bike (up to {@value #SKETCH_RADIUS_CHUNKS} chunks away, never beyond the view distance),
 * which are the chunks that player's client was sent anyway ({@link RideTerrainSketch}). The map is painted from that
 * sketch, so terrain stays on the map after the chunks unload, a map request can never reveal parts of the world its
 * sender has not seen, and nothing is loaded or generated for it. Everything not sketched stays blank map paper.
 *
 * <p>In survival a ride map costs one empty map from the inventory, like any other map; creative players get it free.
 */
final class RideMaps {
	private static final int SKETCH_INTERVAL_TICKS = 20;
	private static final int COOLDOWN_TICKS = 200;
	static final int SKETCH_RADIUS_CHUNKS = 8;
	/** New chunks sketched per rider and second; riding exposes about a dozen a second, standing still none. */
	private static final int SKETCH_BUDGET = 48;

	private static final byte ROUTE = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
	private static final byte START = MapColor.COLOR_LIGHT_GREEN.getPackedId(MapColor.Brightness.HIGH);
	private static final byte FINISH = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL);
	private static final Style LORE = Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY);

	private static final Map<UUID, Map<ResourceKey<Level>, RideTerrainSketch>> SKETCHES = new HashMap<>();
	private static final Map<UUID, Integer> LAST_MAP_TICK = new HashMap<>();

	private RideMaps() {
	}

	/** Sketches the surroundings of every bike rider, once a second. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % SKETCH_INTERVAL_TICKS != 0) {
			return;
		}
		int radius = Math.min(SKETCH_RADIUS_CHUNKS, server.getPlayerList().getViewDistance());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player
				&& bike.level() instanceof ServerLevel level && !level.dimensionType().hasCeiling()) {
				SKETCHES.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
					.computeIfAbsent(level.dimension(), dimension -> new RideTerrainSketch())
					.captureAround(level, bike.blockPosition(), radius, SKETCH_BUDGET);
			}
		}
	}

	static void forget(UUID player) {
		SKETCHES.remove(player);
		LAST_MAP_TICK.remove(player);
	}

	static void handle(RideMapPayload payload, ServerPlayer player, MinecraftServer server) {
		int now = server.getTickCount();
		Integer last = LAST_MAP_TICK.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			player.sendSystemMessage(Component.translatable("voxvelo_fitness_lib.ride.map_cooldown"));
			return;
		}
		Identifier dimensionId = Identifier.tryParse(payload.dimension());
		ServerLevel level = dimensionId == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
		if (level == null) {
			player.sendSystemMessage(Component.translatable("voxvelo_fitness_lib.ride.map_failed"));
			return;
		}
		boolean free = player.hasInfiniteMaterials();
		int emptyMapSlot = free ? -1 : findEmptyMap(player);
		if (!free && emptyMapSlot < 0) {
			player.sendSystemMessage(Component.translatable("voxvelo_fitness_lib.ride.map_needs_paper"));
			return;
		}

		ItemStack map;
		try {
			map = create(level, payload, SKETCHES.getOrDefault(player.getUUID(), Map.of()).get(level.dimension()));
		} catch (RuntimeException e) {
			FitnessMod.LOGGER.warn("Could not create a ride map for {}", player.getName().getString(), e);
			player.sendSystemMessage(Component.translatable("voxvelo_fitness_lib.ride.map_failed"));
			return;
		}
		if (!free) {
			player.getInventory().getItem(emptyMapSlot).shrink(1);
		}
		if (!player.getInventory().add(map)) {
			player.drop(map, false, Prediction.SERVER_ONLY);
		}
		LAST_MAP_TICK.put(player.getUUID(), now);
		player.sendSystemMessage(Component.translatable("voxvelo_fitness_lib.ride.map_given"));
	}

	private static int findEmptyMap(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(Items.MAP)) {
				return slot;
			}
		}
		return -1;
	}

	/** Draws the map and registers its data with the level. Without a sketch the map shows only the route. */
	static ItemStack create(ServerLevel level, RideMapPayload payload, @Nullable RideTerrainSketch sketch) {
		List<RideMapPayload.Point> points = payload.points();
		RideMapLayout layout = RideMapLayout.fit(points);
		byte[] colors = new byte[RideMapLayout.SIZE * RideMapLayout.SIZE];
		if (sketch != null) {
			paintTerrain(layout, colors, sketch);
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
		stack.set(DataComponents.ITEM_NAME, Component.translatable("voxvelo_fitness_lib.ride.map_name",
			String.format(java.util.Locale.ROOT, "%.2f", payload.distanceM() / 1000.0)));
		List<Component> lore = new java.util.ArrayList<>();
		if (!payload.label().isBlank()) {
			lore.add(Component.literal(payload.label()).withStyle(LORE));
		}
		lore.add(Component.translatable("voxvelo_fitness_lib.ride.map_lore_time", formatDuration(payload.timerSeconds())).withStyle(LORE));
		if (payload.avgPowerWatts() != RideMapPayload.NO_POWER) {
			lore.add(Component.translatable("voxvelo_fitness_lib.ride.map_lore_power", payload.avgPowerWatts()).withStyle(LORE));
		}
		lore.add(Component.translatable("voxvelo_fitness_lib.ride.map_lore_ascent", payload.ascentM()).withStyle(LORE));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static String formatDuration(int seconds) {
		int s = Math.max(0, seconds);
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
	}

	/**
	 * Paints every pixel whose block was sketched, shaded like a vanilla map: brighter where the ground rises towards
	 * the north edge, darker where it falls, water darker with depth.
	 */
	private static void paintTerrain(RideMapLayout layout, byte[] colors, RideTerrainSketch sketch) {
		int[] previousRow = new int[RideMapLayout.SIZE];
		java.util.Arrays.fill(previousRow, Integer.MIN_VALUE);
		for (int pz = 0; pz < RideMapLayout.SIZE; pz++) {
			for (int px = 0; px < RideMapLayout.SIZE; px++) {
				RideTerrainSketch.Cell cell = sketch.cell(layout.blockX(px), layout.blockZ(pz));
				if (cell == null || cell.colorId() == 0) {
					previousRow[px] = Integer.MIN_VALUE;
					continue;
				}
				MapColor.Brightness brightness;
				if (cell.isWater()) {
					brightness = MapColor.Brightness.values()[cell.brightness()];
				} else {
					int north = previousRow[px];
					brightness = north == Integer.MIN_VALUE || north == cell.height() ? MapColor.Brightness.NORMAL
						: cell.height() > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
				}
				previousRow[px] = cell.height();
				colors[px + pz * RideMapLayout.SIZE] = MapColor.byId(cell.colorId()).getPackedId(brightness);
			}
		}
	}
}
