package dev.michidk.voxelvelo.client.fitness;

import com.mojang.blaze3d.platform.InputConstants;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/** Key bindings of the fitness integration, listed with the other bike keys in the controls screen. */
public final class FitnessKeys {
	public static final KeyMapping TOGGLE_STATS =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxelvelo.toggle_stats", InputConstants.KEY_H, VoxelVeloKeys.CATEGORY));
	public static final KeyMapping TOGGLE_RECORDING =
		KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voxelvelo.toggle_recording", InputConstants.KEY_R, VoxelVeloKeys.CATEGORY));

	private FitnessKeys() {
	}

	public static void register() {
		// Touching the class registers the key mapping.
	}
}
