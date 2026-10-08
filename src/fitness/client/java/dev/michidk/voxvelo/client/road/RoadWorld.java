package dev.michidk.voxvelo.client.road;

/** What the road follower needs to know about blocks. Kept tiny so the follower can be exercised without a game. */
public interface RoadWorld {
	/** Whether the block at this position is a road block. */
	boolean isRoad(int x, int y, int z);

	/** Whether a bike and rider can occupy this position (no collision shape, no liquid). */
	boolean isPassable(int x, int y, int z);
}
