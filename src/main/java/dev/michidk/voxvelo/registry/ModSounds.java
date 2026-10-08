package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The bike's own sound events. Each one is defined in {@code assets/voxvelo/sounds.json}, which for now points at
 * vanilla sounds as placeholders; real recordings can replace them there without touching code.
 */
public final class ModSounds {
	public static final SoundEvent BIKE_PLACE = register("bike.place");
	public static final SoundEvent BIKE_PICKUP = register("bike.pickup");
	public static final SoundEvent BIKE_MOUNT = register("bike.mount");
	public static final SoundEvent BIKE_DISMOUNT = register("bike.dismount");
	public static final SoundEvent BIKE_GEAR_SHIFT = register("bike.gear_shift");
	public static final SoundEvent BIKE_LAND = register("bike.land");
	public static final SoundEvent BIKE_LAND_HARD = register("bike.land_hard");
	public static final SoundEvent BIKE_CRASH = register("bike.crash");
	public static final SoundEvent BIKE_TIRE_BURST = register("bike.tire_burst");
	public static final SoundEvent BIKE_TIRE_HISS = register("bike.tire_hiss");
	public static final SoundEvent BIKE_REPAIR = register("bike.repair");
	public static final SoundEvent BIKE_BREAK = register("bike.break");
	public static final SoundEvent BIKE_DENY = register("bike.deny");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		Identifier id = VoxVelo.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {
		// Initialize the registered sound events.
	}
}
