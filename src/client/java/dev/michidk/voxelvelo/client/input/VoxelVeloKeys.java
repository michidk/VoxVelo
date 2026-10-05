package dev.michidk.voxelvelo.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.michidk.voxelvelo.VoxelVelo;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Bike key bindings. Minecraft's own movement keys (W/A/S/D by default) also drive the bike, so these
 * add the arrow keys plus the bike-only actions. All of them are rebindable in the controls screen.
 */
public final class VoxelVeloKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VoxelVelo.id("bike"));

	public static final KeyMapping PEDAL = register("key.voxelvelo.pedal", InputConstants.KEY_UP);
	public static final KeyMapping BRAKE = register("key.voxelvelo.brake", InputConstants.KEY_DOWN);
	public static final KeyMapping HARD_BRAKE = register("key.voxelvelo.hard_brake", InputConstants.KEY_LALT);
	public static final KeyMapping STEER_LEFT = register("key.voxelvelo.steer_left", InputConstants.KEY_LEFT);
	public static final KeyMapping STEER_RIGHT = register("key.voxelvelo.steer_right", InputConstants.KEY_RIGHT);
	public static final KeyMapping SHIFT_UP = register("key.voxelvelo.shift_up", InputConstants.KEY_X);
	public static final KeyMapping SHIFT_DOWN = register("key.voxelvelo.shift_down", InputConstants.KEY_Z);

	private VoxelVeloKeys() {
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
	}

	public static void register() {
		// Touching the class registers the key mappings.
	}
}
