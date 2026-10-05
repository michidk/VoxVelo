package dev.michidk.voxelvelo.client;

import dev.michidk.voxelvelo.network.RiderSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.bluetooth.SimpleBleBackend;
import dev.michidk.voxelvelo.client.config.VoxelVeloConfig;
import dev.michidk.voxelvelo.client.ftms.FtmsClient;
import dev.michidk.voxelvelo.client.ftms.TrainerFeedback;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.FtmsTrainerInput;
import dev.michidk.voxelvelo.client.input.KeyboardBikeInput;
import dev.michidk.voxelvelo.client.terrain.RideTerrain;
import dev.michidk.voxelvelo.client.stats.RiderStatsStore;
import dev.michidk.voxelvelo.client.stats.VitalsSender;
import dev.michidk.voxelvelo.client.input.OpenBikeControlInput;
import dev.michidk.voxelvelo.client.input.RoadFollowInput;
import dev.michidk.voxelvelo.client.obc.ObcClient;

/** The client-side services of the mod, created once at startup. Nothing here is ever loaded on a server. */
public final class ClientContext {
	private static ClientContext instance;

	public final VoxelVeloConfig config;
	public final BluetoothManager bluetooth;
	public final FtmsClient ftms;
	public final ObcClient obc;
	public final BikeInputManager inputManager;
	public final RoadFollowInput roadFollow;
	public final TrainerFeedback trainerFeedback;
	public final RideTerrain rideTerrain;
	public final RiderStatsStore riderStats = new RiderStatsStore();
	public final VitalsSender vitalsSender;

	private ClientContext() {
		this.config = VoxelVeloConfig.load();
		// The Bluetooth backend does nothing (and loads no native code) until first used.
		this.bluetooth = new BluetoothManager(new SimpleBleBackend());
		this.ftms = new FtmsClient(this.bluetooth);
		this.obc = new ObcClient(this.bluetooth);
		this.inputManager = new BikeInputManager(this.config, new KeyboardBikeInput(this.config));
		this.inputManager.register(new FtmsTrainerInput(this.ftms));
		this.inputManager.register(new OpenBikeControlInput(this.obc));
		this.roadFollow = new RoadFollowInput(this.config, this.inputManager);
		this.inputManager.register(this.roadFollow);
		this.rideTerrain = new RideTerrain(this.config);
		this.vitalsSender = new VitalsSender(this);
		this.trainerFeedback = new TrainerFeedback(this);
	}

	/** Tells the server this player rider mass and speed limit. Harmless if the server does not know the packet. */
	public void sendRiderSettings() {
		if (ClientPlayNetworking.canSend(RiderSettingsPayload.TYPE)) {
			ClientPlayNetworking.send(RiderSettingsPayload.of(this.config.riderMassKg, this.config.speedLimitKmh));
		}
	}

	public static ClientContext init() {
		instance = new ClientContext();
		return instance;
	}

	public static ClientContext get() {
		return instance;
	}
}
