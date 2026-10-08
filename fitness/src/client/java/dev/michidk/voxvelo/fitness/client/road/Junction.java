package dev.michidk.voxvelo.fitness.client.road;

import java.util.List;

/**
 * A crossing, fork or turn-off ahead on the road.
 *
 * @param centreX world x of the middle of the junction
 * @param centreZ world z of the middle of the junction
 * @param y       ground height at the middle
 * @param radius  distance from the middle at which the exits were measured
 * @param entryX  unit heading of the approach in world x
 * @param entryZ  unit heading of the approach in world z
 * @param exits   every way on, as seen from the approach (the way the bike came from is not listed)
 */
public record Junction(double centreX, double centreZ, int y, double radius, double entryX, double entryZ, List<Exit> exits) {
	public enum Side {
		LEFT, STRAIGHT, RIGHT
	}

	/**
	 * @param side  where this exit lies relative to the approach
	 * @param angle degrees from the approach heading, negative to the left
	 * @param width how wide the road is where it leaves the junction, in blocks
	 * @param dirX  unit direction of the exit in world x
	 * @param dirZ  unit direction of the exit in world z
	 */
	public record Exit(Side side, double angle, double width, double dirX, double dirZ) {
	}

	/** A single way on that is not straight ahead: a corner, nothing to choose. */
	public boolean forced() {
		return this.exits.size() == 1;
	}

	/** The exit for a side: the leftmost or rightmost one, or for STRAIGHT the one closest to the heading. Null if none. */
	public Exit exit(Side side) {
		Exit found = null;
		for (Exit exit : this.exits) {
			if (side == Side.STRAIGHT) {
				if (found == null || Math.abs(exit.angle()) < Math.abs(found.angle())) {
					found = exit;
				}
			} else if (exit.side() == side) {
				return exit;
			}
		}
		return side == Side.STRAIGHT && found != null && found.side() == Side.STRAIGHT ? found : null;
	}
}
