package dev.michidk.voxelfitness.api;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxelfitness.client.FitnessSettingsScreen;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Public client API, version 1. All calls must run on the Minecraft client thread. */
public final class Fitness {
	public static final int API_VERSION = 1;

	private Fitness() {}

	public static AutoCloseable register(VehicleAdapter adapter) {
		return FitnessRuntime.get().vehicles.register(adapter);
	}

	public static FitnessControls controls() {
		return FitnessRuntime.get().controls();
	}

	public static Optional<VehicleTelemetry> vehicle() {
		return Optional.ofNullable(FitnessRuntime.get().telemetry());
	}

	public static void startSession() {
		FitnessRuntime.get().rideRecorder.start();
	}

	/** Ends the current session and exports it using the user's FIT settings. */
	public static void stopSession() {
		FitnessRuntime.get().saveRide();
	}

	public static boolean isRecording() {
		return FitnessRuntime.get().rideRecorder.isRecording();
	}

	public static double sessionDistanceM() {
		return FitnessRuntime.get().rideRecorder.distanceM();
	}

	public static void openSettings(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new FitnessSettingsScreen(parent));
	}
}
