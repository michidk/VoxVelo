package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.bluetooth.SimpleBleBackend;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import dev.michidk.voxelvelo.client.ftms.FtmsClient;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.FtmsTrainerInput;
import dev.michidk.voxelvelo.client.input.KeyboardBikeInput;

/** The client-side services of the mod, created once at startup. Nothing here is ever loaded on a server. */
public final class ClientContext {
	private static ClientContext instance;

	public final VoxelVeloConfig config;
	public final BluetoothManager bluetooth;
	public final FtmsClient ftms;
	public final BikeInputManager inputManager;

	private ClientContext() {
		this.config = VoxelVeloConfig.load();
		// The Bluetooth backend does nothing (and loads no native code) until first used.
		this.bluetooth = new BluetoothManager(new SimpleBleBackend());
		this.ftms = new FtmsClient(this.bluetooth);
		this.inputManager = new BikeInputManager(this.config, new KeyboardBikeInput(this.config));
		this.inputManager.register(new FtmsTrainerInput(this.ftms));
	}

	public static ClientContext init() {
		instance = new ClientContext();
		return instance;
	}

	public static ClientContext get() {
		return instance;
	}
}
