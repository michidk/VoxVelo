package dev.michidk.voxvelo.fitness;

import dev.michidk.voxvelo.network.RideMapPayload;
import java.util.List;

/**
 * Where a ride goes on a 128 x 128 map, without touching the world (so it can be checked on its own).
 *
 * <p>The map is centred on the route. When the route fits a vanilla map scale (1 to 16 blocks per pixel, keeping a
 * small margin) that scale is used and the centre is a multiple of it, so the map lines up with the world exactly
 * like a vanilla map and shows the holder's position. A longer ride does not fit even the largest vanilla scale;
 * it is then fitted at whatever blocks-per-pixel it needs and the map does not track players.
 *
 * @param centerX        block at the map's centre
 * @param centerZ        block at the map's centre
 * @param blocksPerPixel how many blocks one pixel covers
 * @param scale          the vanilla scale (0..4), meaningful when {@code aligned}
 * @param aligned        whether the map matches a vanilla map of that scale and centre
 */
public record RideMapLayout(int centerX, int centerZ, double blocksPerPixel, int scale, boolean aligned) {
	public static final int SIZE = 128;
	private static final int HALF = SIZE / 2;
	/** Pixels kept free on each side so the line and the markers are not cut off at the edge. */
	private static final int MARGIN = 6;
	private static final int MAX_SCALE = 4;

	public static RideMapLayout fit(List<RideMapPayload.Point> points) {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (RideMapPayload.Point point : points) {
			minX = Math.min(minX, point.x());
			maxX = Math.max(maxX, point.x());
			minZ = Math.min(minZ, point.z());
			maxZ = Math.max(maxZ, point.z());
		}
		long extent = Math.max((long) maxX - minX, (long) maxZ - minZ) + 1;
		long midX = Math.floorDiv((long) minX + maxX, 2);
		long midZ = Math.floorDiv((long) minZ + maxZ, 2);
		int usable = SIZE - 2 * MARGIN;
		for (int scale = 0; scale <= MAX_SCALE; scale++) {
			int blocks = 1 << scale;
			if (extent <= (long) usable * blocks) {
				// A centre on the pixel grid keeps the pixel edges on block edges, as on a vanilla map.
				return new RideMapLayout((int) (Math.floorDiv(midX, blocks) * blocks), (int) (Math.floorDiv(midZ, blocks) * blocks),
					blocks, scale, true);
			}
		}
		return new RideMapLayout((int) midX, (int) midZ, (double) extent / usable, MAX_SCALE, false);
	}

	/** Map column of a block x (may be outside 0..127). */
	public int pixelX(double x) {
		return (int) Math.floor((x - this.centerX) / this.blocksPerPixel) + HALF;
	}

	public int pixelZ(double z) {
		return (int) Math.floor((z - this.centerZ) / this.blocksPerPixel) + HALF;
	}

	/** Block x at the middle of a map column, where its terrain colour is sampled. */
	public int blockX(int pixel) {
		return (int) Math.floor(this.centerX + (pixel - HALF + 0.5) * this.blocksPerPixel);
	}

	public int blockZ(int pixel) {
		return (int) Math.floor(this.centerZ + (pixel - HALF + 0.5) * this.blocksPerPixel);
	}

	/**
	 * Draws the route into {@code colors} (row-major, x fastest, as in map data): a two-pixel line in {@code line},
	 * a three by three dot in {@code start} at the first point and one in {@code finish} at the last point unless the
	 * ride ended where it started.
	 */
	public void drawRoute(byte[] colors, List<RideMapPayload.Point> points, byte line, byte start, byte finish) {
		RideMapPayload.Point previous = null;
		for (RideMapPayload.Point point : points) {
			int x = this.pixelX(point.x());
			int z = this.pixelZ(point.z());
			if (previous == null || point.newStretch()) {
				brush(colors, x, z, line);
			} else {
				line(colors, this.pixelX(previous.x()), this.pixelZ(previous.z()), x, z, line);
			}
			previous = point;
		}
		if (points.isEmpty()) {
			return;
		}
		RideMapPayload.Point first = points.getFirst();
		RideMapPayload.Point last = points.getLast();
		int sx = this.pixelX(first.x());
		int sz = this.pixelZ(first.z());
		int ex = this.pixelX(last.x());
		int ez = this.pixelZ(last.z());
		if (Math.max(Math.abs(ex - sx), Math.abs(ez - sz)) > 3) {
			dot(colors, ex, ez, finish);
		}
		dot(colors, sx, sz, start);
	}

	/** Bresenham line with the two-pixel brush. */
	private static void line(byte[] colors, int x0, int z0, int x1, int z1, byte color) {
		int dx = Math.abs(x1 - x0);
		int dz = -Math.abs(z1 - z0);
		int stepX = x0 < x1 ? 1 : -1;
		int stepZ = z0 < z1 ? 1 : -1;
		int error = dx + dz;
		int x = x0;
		int z = z0;
		// The layout fits every point, so a line is never longer than the map; the bound is only a safety net.
		for (int guard = 0; guard <= 2 * SIZE; guard++) {
			brush(colors, x, z, color);
			if (x == x1 && z == z1) {
				return;
			}
			int doubled = 2 * error;
			if (doubled >= dz) {
				error += dz;
				x += stepX;
			}
			if (doubled <= dx) {
				error += dx;
				z += stepZ;
			}
		}
	}

	private static void brush(byte[] colors, int x, int z, byte color) {
		set(colors, x, z, color);
		set(colors, x + 1, z, color);
		set(colors, x, z + 1, color);
		set(colors, x + 1, z + 1, color);
	}

	private static void dot(byte[] colors, int x, int z, byte color) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oz = -1; oz <= 1; oz++) {
				set(colors, x + ox, z + oz, color);
			}
		}
	}

	private static void set(byte[] colors, int x, int z, byte color) {
		if (x >= 0 && x < SIZE && z >= 0 && z < SIZE) {
			colors[x + z * SIZE] = color;
		}
	}
}
