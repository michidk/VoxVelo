package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.bluetooth.SimpleBleBackend;
import dev.michidk.voxelvelo.client.ftms.FtmsClient;
import dev.michidk.voxelvelo.client.ftms.TrainerFeedback;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.FtmsTrainerInput;
import dev.michidk.voxelvelo.client.input.OpenBikeControlInput;
import dev.michidk.voxelvelo.client.input.RoadFollowInput;
import dev.michidk.voxelvelo.client.obc.ObcClient;
import dev.michidk.voxelvelo.client.powermeter.PowerMeterClient;
import dev.michidk.voxelvelo.client.ride.RideRecorder;
import dev.michidk.voxelvelo.client.stats.RiderStatsStore;
import dev.michidk.voxelvelo.client.stats.VitalsSender;
import dev.michidk.voxelvelo.client.terrain.RideTerrain;

/** The client-side services of the fitness integration, created once at startup next to the core ones. */
public final class FitnessContext {
	private static FitnessContext instance;

	/** The core services, for the settings that belong to the raw bike mod. */
	public final ClientContext core;
	public final FitnessConfig config;
	public final BluetoothManager bluetooth;
	public final FtmsClient ftms;
	public final PowerMeterClient powerMeter;
	public final ObcClient obc;
	public final BikeInputManager inputManager;
	public final RoadFollowInput roadFollow;
	public final TrainerFeedback trainerFeedback;
	public final RideTerrain rideTerrain;
	public final RiderStatsStore riderStats = new RiderStatsStore();
	public final VitalsSender vitalsSender;
	public final RideRecorder rideRecorder;

	private FitnessContext(ClientContext core) {
		this.core = core;
		this.config = FitnessConfig.load();
		// The Bluetooth backend does nothing (and loads no native code) until first used.
		this.bluetooth = new BluetoothManager(new SimpleBleBackend());
		this.ftms = new FtmsClient(this.bluetooth);
		this.powerMeter = new PowerMeterClient(this.bluetooth);
		this.obc = new ObcClient(this.bluetooth);
		this.inputManager = core.inputManager;
		this.inputManager.register(new FtmsTrainerInput(this.ftms, this.powerMeter));
		this.inputManager.register(new OpenBikeControlInput(this.obc));
		this.roadFollow = new RoadFollowInput(this.config, this.inputManager);
		this.inputManager.register(this.roadFollow);
		this.inputManager.setSelection(this.config::sourceFor);
		this.rideTerrain = new RideTerrain(this.config);
		this.vitalsSender = new VitalsSender(this);
		this.trainerFeedback = new TrainerFeedback(this);
		this.rideRecorder = new RideRecorder(this);
	}

	static FitnessContext init(ClientContext core) {
		instance = new FitnessContext(core);
		return instance;
	}

	public static FitnessContext get() {
		return instance;
	}
}
