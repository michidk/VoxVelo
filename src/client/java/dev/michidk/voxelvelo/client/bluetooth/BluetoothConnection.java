package dev.michidk.voxelvelo.client.bluetooth;

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

	/** Subscribes to notifications (or indications). The handler runs on a Bluetooth thread, not the game thread. */
	CompletableFuture<Void> subscribe(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler);

	void disconnect();
}
