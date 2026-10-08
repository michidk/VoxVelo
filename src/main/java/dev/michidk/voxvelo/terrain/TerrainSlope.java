package dev.michidk.voxvelo.terrain;

import java.util.OptionalDouble;

/**
 * Slope of the ground under and around a bike, measured over many blocks. A trainer reacts slowly, so it
 * should feel the trend of the road (a ramp coming up, a long climb) rather than every single block step.
 *
 * <p>Pure logic over a {@link Ground} source, so it can be exercised without a running game.
 */
public final class TerrainSlope {
	/** At least this many window blocks in each direction; shorter windows follow single steps too closely. */
	public static final int MIN_WINDOW = 10;
	public static final int MAX_WINDOW = 40;

	/** Looks up the walkable surface height of a block column. */
	public interface Ground {
		/**
		 * @param x           block column x
		 * @param z           block column z
		 * @param referenceY  surface height near where the previous column ended, to stay on the same surface
		 * @return the surface height (block top, may be fractional for slabs), if there is ground
		 */
		OptionalDouble height(int x, int z, double referenceY);
	}

	private TerrainSlope() {
	}

	/**
	 * Least-squares slope (rise over run) of the surface along the heading, from {@code window} blocks behind to
	 * {@code window} blocks ahead of the position. Columns without ground are skipped; fewer than five usable
	 * samples give no result.
	 *
	 * @param px,pz   position in blocks
	 * @param fx,fz   unit heading vector in the horizontal plane
	 * @param startY  surface height at the position
	 */
	public static OptionalDouble measure(Ground ground, double px, double pz, double fx, double fz, double startY, int window) {
		return measureWindow(ground, px, pz, fx, fz, startY, Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, window)));
	}

	/**
	 * Like {@link #measure} but over exactly {@code window} blocks each way, however short. The bike physics uses a
	 * short window, since gravity should follow the slope under the wheels rather than the trend of the whole road.
	 */
	public static OptionalDouble measureWindow(Ground ground, double px, double pz, double fx, double fz, double startY, int window) {
		int w = Math.max(1, window);
		int count = 2 * w + 1;
		double[] heights = new double[count];
		boolean[] known = new boolean[count];

		OptionalDouble centre = ground.height(floor(px), floor(pz), startY);
		double centreY = centre.orElse(startY);
		heights[w] = centreY;
		known[w] = centre.isPresent();

		for (int direction = -1; direction <= 1; direction += 2) {
			double reference = centreY;
			for (int d = 1; d <= w; d++) {
				int x = floor(px + fx * d * direction);
				int z = floor(pz + fz * d * direction);
				OptionalDouble h = ground.height(x, z, reference);
				int index = w + d * direction;
				if (h.isPresent()) {
					heights[index] = h.getAsDouble();
					known[index] = true;
					reference = h.getAsDouble();
				}
			}
		}

		int n = 0;
		double sumD = 0.0, sumH = 0.0;
		for (int i = 0; i < count; i++) {
			if (known[i]) {
				n++;
				sumD += i - w;
				sumH += heights[i];
			}
		}
		if (n < 5) {
			return OptionalDouble.empty();
		}
		double meanD = sumD / n;
		double meanH = sumH / n;
		double covariance = 0.0, variance = 0.0;
		for (int i = 0; i < count; i++) {
			if (known[i]) {
				double dd = (i - w) - meanD;
				covariance += dd * (heights[i] - meanH);
				variance += dd * dd;
			}
		}
		return variance < 1.0e-9 ? OptionalDouble.empty() : OptionalDouble.of(covariance / variance);
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}
}
