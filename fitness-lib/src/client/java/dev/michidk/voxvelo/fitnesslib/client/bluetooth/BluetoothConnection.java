package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A connected GATT peripheral. All methods are non-blocking and safe to call from any thread. */
public interface BluetoothConnection {
	String deviceId();

	boolean isConnected();

	/** Lower-case 128-bit UUIDs of the services discovered on the device. */
	Set<String> serviceUuids();

	CompletableFuture<byte[]> read(String serviceUuid, String characteristicUuid);

	CompletableFuture<Void> write(String serviceUuid, String characteristicUuid, byte[] data, boolean withResponse);

	/**
	 * Subscribes to a characteristic, notifications or indications as its properties allow. The handler runs on a
	 * Bluetooth thread, not the game thread.
	 */
	CompletableFuture<Void> subscribe(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler);

	/**
	 * Subscribes to indications specifically, for characteristics a specification defines as indicate-only (such as the
	 * Fitness Machine Control Point). Nothing is inferred from what the device reported about itself.
	 */
	CompletableFuture<Void> indicate(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler);

	/** Closes the connection. The future completes once the platform has actually let go of the device. */
	CompletableFuture<Void> disconnect();
}
