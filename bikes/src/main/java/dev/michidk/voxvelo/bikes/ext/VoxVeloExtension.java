package dev.michidk.voxvelo.bikes.ext;

/**
 * Hook for add-on features that are not part of the raw bike mod, such as the fitness integration. Implementations
 * are declared under the {@code voxvelo_bikes-extension} entrypoint of the module metadata, so the core build never
 * references them and the full build can plug them in.
 */
public interface VoxVeloExtension {
	String ENTRYPOINT = "voxvelo_bikes-extension";

	/** Runs on both client and server, after the core content is registered. */
	void onInitialize();
}
