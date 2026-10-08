package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Platform Bluetooth LE access. Everything the rest of the mod knows about Bluetooth goes through this
 * interface, so the underlying library can be replaced without touching FTMS or UI code.
 *
 * <p>Implementations must never block the caller: scanning and connecting happen on their own threads.
 */
public interface BluetoothBackend {
	/** Whether a usable adapter exists right now. May change over time (user toggles Bluetooth). */
	boolean isAvailable();

	/** Human-readable explanation when {@link #isAvailable()} is false. */
	String unavailableReason();

	/** Starts scanning. {@code onDevice} is called for every discovery and update, from a Bluetooth thread. */
	void startScan(Consumer<BluetoothDevice> onDevice);

	void stopScan();

	boolean isScanning();

	/** Connects to a device previously reported by the scan. {@code onDisconnected} fires once if the link drops later. */
	CompletableFuture<BluetoothConnection> connect(String deviceId, Runnable onDisconnected);

	void shutdown();
}
