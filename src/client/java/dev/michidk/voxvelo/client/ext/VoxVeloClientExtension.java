package dev.michidk.voxvelo.client.ext;

import dev.michidk.voxvelo.client.ClientContext;
import net.minecraft.client.Minecraft;

/**
 * Hook for client-side add-ons that are not part of the raw bike mod, such as the fitness integration. Implementations
 * are declared under the {@code voxvelo-client-extension} entrypoint of the module metadata, so the core build
 * never references them and the full build can plug them in.
 */
public interface VoxVeloClientExtension {
	String ENTRYPOINT = "voxvelo-client-extension";

	/**
	 * Called once at client start-up, after the core services exist. Register input sources, HUD elements, packet
	 * receivers, key bindings, settings pages and HUD additions here.
	 */
	void init(ClientContext ctx);

	/** Called at the end of every client tick, before the controls are composed and sent. */
	default void tick(Minecraft client) {
	}

	/** Called when the player joins a world or server. */
	default void onJoin() {
	}

	/** Called when the game is closing; release devices and save settings. */
	default void onStopping() {
	}
}
