package dev.michidk.voxvelo.bikes.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.michidk.voxvelo.bikes.VoxVelo;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Bike key bindings. Minecraft's own movement keys (W/A/S/D by default) also drive the bike, so these
 * add the arrow keys plus the bike-only actions. All of them are rebindable in the controls screen. Add-ons register
 * their own keys next to these.
 */
public final class VoxVeloKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VoxVelo.id("bike"));

	public static final KeyMapping PEDAL = register("key.voxvelo_bikes.pedal", InputConstants.KEY_UP);
	public static final KeyMapping BRAKE = register("key.voxvelo_bikes.brake", InputConstants.KEY_DOWN);
	public static final KeyMapping HARD_BRAKE = register("key.voxvelo_bikes.hard_brake", InputConstants.KEY_LALT);
	public static final KeyMapping STEER_LEFT = register("key.voxvelo_bikes.steer_left", InputConstants.KEY_LEFT);
	public static final KeyMapping STEER_RIGHT = register("key.voxvelo_bikes.steer_right", InputConstants.KEY_RIGHT);
	public static final KeyMapping OPEN_CONFIG = register("key.voxvelo_bikes.open_config", InputConstants.KEY_B);

	private VoxVeloKeys() {
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
	}

	public static void register() {
		// Touching the class registers the key mappings.
	}
}
