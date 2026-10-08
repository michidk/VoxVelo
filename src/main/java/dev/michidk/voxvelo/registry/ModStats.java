package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/** Vanilla player statistics, shared by the core and fitness builds. */
public final class ModStats {
	public static final Identifier DISTANCE_CYCLED = register("distance_cycled", StatFormatter.DISTANCE);
	public static final Identifier TIME_ON_BICYCLE = register("time_on_bicycle", StatFormatter.TIME);

	private ModStats() {}

	private static Identifier register(String name, StatFormatter formatter) {
		Identifier id = VoxVelo.id(name);
		Registry.register(BuiltInRegistries.CUSTOM_STAT, id, id);
		Stats.CUSTOM.get(id, formatter);
		return id;
	}

	public static void register() {
		// Initialize the registered statistics and their display formatters.
	}
}
