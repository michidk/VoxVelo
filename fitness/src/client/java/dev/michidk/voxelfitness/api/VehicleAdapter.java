package dev.michidk.voxelfitness.api;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Client-thread callbacks. Never send hardware commands here; the fitness runtime owns that
 * lifecycle.
 */
public interface VehicleAdapter {
	String id();

	/** True only when this player controls this vehicle, including passenger/driver rules. */
	boolean supports(LocalPlayer player, Entity vehicle);

	VehicleTelemetry sample(LocalPlayer player, Entity vehicle);

	/** Called once per tick for the selected adapter, with edge-triggered shifts. */
	default void applyControls(LocalPlayer player, Entity vehicle, FitnessControls controls) {}

	/** Called on dismount, pause, disconnect, vehicle/adapter replacement or a callback failure. */
	default void deactivate() {}
}
