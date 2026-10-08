package dev.michidk.voxvelo.fitness.fitness;

import dev.michidk.voxvelo.bikes.ext.VoxVeloExtension;

/** Common (client and server) side of the fitness integration: rider vitals and stats sharing. */
public final class FitnessExtension implements VoxVeloExtension {
	@Override
	public void onInitialize() {
		FitnessNetworking.register();
	}
}
