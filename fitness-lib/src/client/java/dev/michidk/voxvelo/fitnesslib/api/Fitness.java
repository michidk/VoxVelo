package dev.michidk.voxvelo.fitnesslib.api;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.FitnessSettingsScreen;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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

	/** Adds a page to the fitness settings, after the built-in ones. Call once, from a client initializer. */
	public static void addSettingsPage(String labelKey, Function<Screen, Screen> factory) {
		FitnessSettingsScreen.PAGES.add(new SettingsPage(labelKey, factory));
	}

	/** The pages of the fitness settings in order, for a mod that lists them in its own settings. */
	public static List<SettingsPage> settingsPages() {
		return List.copyOf(FitnessSettingsScreen.PAGES);
	}
}
