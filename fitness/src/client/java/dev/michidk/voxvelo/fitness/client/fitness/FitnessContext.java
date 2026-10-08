package dev.michidk.voxvelo.fitness.client.fitness;

import dev.michidk.voxvelo.bikes.client.ClientContext;
import dev.michidk.voxvelo.bikes.client.input.BikeInputManager;
import dev.michidk.voxvelo.fitness.client.input.RoadFollowInput;
import dev.michidk.voxvelo.fitness.client.stats.RiderStatsStore;
import dev.michidk.voxvelo.fitness.client.stats.VitalsSender;
import dev.michidk.voxvelo.fitness.client.terrain.RideTerrain;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothManager;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.fitnesslib.client.ftms.FtmsClient;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerFeedback;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRateClient;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcClient;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.PowerMeterClient;
import dev.michidk.voxvelo.fitnesslib.client.ride.RideRecorder;

/**
 * The client-side services of the fitness integration, created once at startup next to the core
 * ones.
 */
public final class FitnessContext {
	private static FitnessContext instance;

	/** The core services, for the settings that belong to the raw bike mod. */
	public final ClientContext core;

	public final FitnessConfig config;
	public final BluetoothManager bluetooth;
	public final FtmsClient ftms;
	public final PowerMeterClient powerMeter;
	public final HeartRateClient heartRate;
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
		var runtime = dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime.get();
		this.config = runtime.config;
		this.bluetooth = runtime.bluetooth;
		this.ftms = runtime.ftms;
		this.powerMeter = runtime.powerMeter;
		this.heartRate = runtime.heartRate;
		this.obc = runtime.obc;
		this.inputManager = core.inputManager;
		BicycleFitnessAdapter adapter = new BicycleFitnessAdapter(this);
		dev.michidk.voxvelo.fitnesslib.api.Fitness.register(adapter);
		this.inputManager.register(adapter);
		this.roadFollow = new RoadFollowInput(this.config, this.inputManager);
		this.inputManager.register(this.roadFollow);
		this.rideTerrain = new RideTerrain(this.config);
		this.vitalsSender = new VitalsSender(this);
		this.trainerFeedback = runtime.trainerFeedback;
		this.rideRecorder = runtime.rideRecorder;
	}

	static FitnessContext init(ClientContext core) {
		instance = new FitnessContext(core);
		return instance;
	}

	public static FitnessContext get() {
		return instance;
	}
}
