package dev.michidk.voxvelo.fitness.client.road;

import dev.michidk.voxvelo.bikes.bike.BikeType;
import java.util.Random;

/** Road acquisition/loss and junction lifecycle over a small synthetic block world. */
public final class RoadChecks {
	public static void main(String[] args) {
		World world = new World();
		var params = BikeType.ROAD.params();
		var centred = RoadFollower.steer(world, 0.5, 65, 0, 0, 4, params, 5);
		require(centred.hasTarget() && centred.onRoad() && Math.abs(centred.steering()) < 0.01, "straight road holds centre");
		var offset = RoadFollower.steer(world, 1.5, 65, 0, 0, 4, params, 5);
		require(offset.hasTarget() && offset.steering() > 0, "offset rider steers toward road centre");
		world.available = false;
		var lost = RoadFollower.steer(world, 0.5, 65, 0, 0, 4, params, 5);
		require(!lost.hasTarget() && lost.steering() == 0, "lost or unloaded road yields no steering");
		world.available = true;
		world.blocked = true;
		require(!RoadFollower.steer(world, 0.5, 65, 0, 0, 4, params, 5).hasTarget(), "blocked headroom is not rideable");
		world.blocked = false;
		world.junction = true;
		JunctionNavigator nav = new JunctionNavigator(new Random(0));
		var settings = new JunctionNavigator.Settings(true, 0, 5);
		JunctionNavigator.Guidance guidance = JunctionNavigator.Guidance.IDLE;
		for (int i = 0; i < 8; i++) guidance = nav.update(world, 0.5, 65, 0, 0, 4, 0, settings, params);
		require(guidance.phase() == JunctionNavigator.Phase.PROMPTING && guidance.prompt() != null, "junction discovery opens a choice prompt");
		guidance = nav.update(world, 0.5, 65, 0, 0, 4, -1, settings, params);
		require(guidance.prompt().chosen() == Junction.Side.LEFT, "neutral followed by left selects the left exit");
		guidance = nav.update(world, 0.5, 65, 14, 0, 4, 0, settings, params);
		require(guidance.phase() == JunctionNavigator.Phase.TURNING && guidance.target(), "approaching the junction starts the selected turn");
		nav.reset();
		world.junction = false;
		guidance = nav.update(world, 0.5, 65, 0, 0, 4, 0, settings, params);
		require(guidance.phase() == JunctionNavigator.Phase.IDLE && guidance.prompt() == null && !guidance.target(), "dismount reset clears turn guidance");
		world.junction = true;
		for (int i = 0; i < 8; i++) guidance = nav.update(world, 0.5, 65, 0, 0, 4, 0, settings, params);
		require(guidance.phase() == JunctionNavigator.Phase.PROMPTING, "remount can acquire a junction again");
		guidance = nav.update(world, 0.5, 65, -40, 0, 4, 0, settings, params);
		require(guidance.phase() == JunctionNavigator.Phase.IDLE, "moving away cancels a pending junction");
		System.out.println("Road following lifecycle checks passed.");
	}

	private static final class World implements RoadWorld {
		boolean available = true;
		boolean blocked;
		boolean junction;
		public boolean isRoad(int x, int y, int z) {
			return available && y == 64 && (Math.abs(x) <= 2 || junction && z >= 18 && z <= 22);
		}
		public boolean isPassable(int x, int y, int z) { return !blocked && y > 64; }
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
