package dev.michidk.voxelvelo.fitness;

import dev.michidk.voxelvelo.ext.VoxelVeloExtension;

/** Common (client and server) side of the fitness integration: rider vitals and stats sharing. */
public final class FitnessExtension implements VoxelVeloExtension {
	@Override
	public void onInitialize() {
		FitnessNetworking.register();
	}
}
