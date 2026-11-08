package dev.michidk.voxvelo.fitness.fitness;

import dev.michidk.voxvelo.fitness.network.RideMapPayload;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Draws a ride map: the route as a red line with a green start and a black finish, over the terrain it passed
 * through. The server paints map items with it and the rider's client a PNG in the ride's folder, so both look the
 * same.
 */
public final class RideMapImage {
	private static final byte ROUTE = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
	private static final byte START = MapColor.COLOR_LIGHT_GREEN.getPackedId(MapColor.Brightness.HIGH);
	private static final byte FINISH = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL);
	/** Blank map paper, where nothing was sketched, as on a map item. */
	public static final int PAPER = 0xFFE5DCC1;

	/**
	 * @param layout where the route sits on the map
	 * @param colors packed map colours, row-major with x fastest, {@link RideMapLayout#SIZE} square
	 */
	public record Drawn(RideMapLayout layout, byte[] colors) {
		/** The pixel at map column {@code x} and row {@code z} as ARGB, paper where nothing is drawn. */
		public int argb(int x, int z) {
			return RideMapImage.argb(this.colors[x + z * RideMapLayout.SIZE]);
		}
	}

	private RideMapImage() {
	}

	/** Lays the route out on the map and paints it, over the sketched terrain when there is a sketch. */
	public static Drawn draw(List<RideMapPayload.Point> points, @Nullable RideTerrainSketch sketch) {
		RideMapLayout layout = RideMapLayout.fit(points);
		byte[] colors = new byte[RideMapLayout.SIZE * RideMapLayout.SIZE];
		if (sketch != null) {
			paintTerrain(layout, colors, sketch);
		}
		layout.drawRoute(colors, points, ROUTE, START, FINISH);
		return new Drawn(layout, colors);
	}

	/** A packed map colour as ARGB; the colourless ids (nothing drawn) show as paper. */
	public static int argb(byte packed) {
		int id = packed & 0xFF;
		return id >> 2 == 0 ? PAPER : MapColor.getColorFromPackedId(id);
	}

	/**
	 * Paints every pixel whose block was sketched, shaded like a vanilla map: brighter where the ground rises towards
	 * the north edge, darker where it falls, water darker with depth.
	 */
	private static void paintTerrain(RideMapLayout layout, byte[] colors, RideTerrainSketch sketch) {
		int[] previousRow = new int[RideMapLayout.SIZE];
		Arrays.fill(previousRow, Integer.MIN_VALUE);
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
