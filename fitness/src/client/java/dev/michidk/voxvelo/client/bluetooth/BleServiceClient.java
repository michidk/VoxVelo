package dev.michidk.voxvelo.client.bluetooth;

import dev.michidk.voxvelo.client.fitness.FitnessConfig;
import java.util.concurrent.Executor;
import net.minecraft.network.chat.Component;

/**
 * A client for one standard Bluetooth service, such as a trainer, power meter or heart rate sensor: connects to the
 * device the player picks and reconnects to the saved one. Subclasses only decode the data.
 */
public abstract class BleServiceClient extends DeviceClient {
	private final BleProfile profile;

	protected BleServiceClient(BluetoothManager bluetooth, Executor gameThread, String logName, BleProfile profile) {
		super(bluetooth, gameThread, logName);
		this.profile = profile;
	}

	/** Connects on the player request (UI). Clears any earlier deliberate disconnect. */
	public void connect(BluetoothDevice device) {
		this.playerRequested();
		this.start(device.id(), device.displayName());
	}

	/** Called every client tick while a world is loaded. Starts auto-connect and reconnect attempts. */
	public abstract void tick(FitnessConfig config);

	/** Tries the saved device now and then, while auto-connect is on for it. */
	protected final void autoConnect(boolean enabled, String savedId, String savedName) {
		if (this.autoAttemptDue(enabled && !savedId.isEmpty())) {
			this.start(savedId, savedName);
		}
	}

	private void start(String deviceId, String name) {
		int gen = this.begin(name, Component.translatable("voxvelo.connection.searching"));
		this.connectBluetooth(gen, deviceId, this.profile, this::onData);
	}

	/** Handles one notification of the subscribed characteristic, on the game thread. */
	protected abstract void onData(byte[] data);
}
