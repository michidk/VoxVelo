package dev.michidk.voxvelo.fitnesslib.client.fitness;

import com.mojang.blaze3d.platform.InputConstants;
import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Key bindings of the fitness integration, listed with the other bike keys in the controls screen. */
public final class FitnessKeys {
public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(FitnessRuntime.MOD_ID, "fitness"));
public static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxvelo_fitness_lib.settings", InputConstants.KEY_F8, CATEGORY));
	public static final KeyMapping TOGGLE_STATS =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxvelo_fitness_lib.toggle_stats", InputConstants.KEY_H, CATEGORY));
	public static final KeyMapping TOGGLE_RECORDING =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxvelo_fitness_lib.toggle_recording", InputConstants.KEY_R, CATEGORY));
	public static final KeyMapping SHIFT_UP =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxvelo_fitness_lib.shift_up", InputConstants.KEY_X, CATEGORY));
	public static final KeyMapping SHIFT_DOWN =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxvelo_fitness_lib.shift_down", InputConstants.KEY_Z, CATEGORY));

	private FitnessKeys() {
	}

	public static void register() {
		// Touching the class registers the key mapping.
	}
}
