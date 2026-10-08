package dev.michidk.voxvelo.fitnesslib.client;

import dev.michidk.voxvelo.fitnesslib.api.*;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.*;
import dev.michidk.voxvelo.fitnesslib.client.fitness.*;
import dev.michidk.voxvelo.fitnesslib.client.ftms.*;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRateClient;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcClient;
import dev.michidk.voxvelo.fitnesslib.client.obc.ObcMenuNavigation;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.PowerMeterClient;
import dev.michidk.voxvelo.fitnesslib.client.ride.*;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal service owner; external vehicle mods integrate through Fitness, not device clients. */
public final class FitnessRuntime {
	public static final String MOD_ID = "voxvelo_fitness_lib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static FitnessRuntime instance;
	public final FitnessConfig config = FitnessConfig.load();
	public final BluetoothManager bluetooth = new BluetoothManager(new BtleplugBackend());
	public final FtmsClient ftms = new FtmsClient(bluetooth, DeviceClient.GAME_THREAD);
	public final PowerMeterClient powerMeter =
			new PowerMeterClient(bluetooth, DeviceClient.GAME_THREAD);
	public final HeartRateClient heartRate =
			new HeartRateClient(bluetooth, DeviceClient.GAME_THREAD);
	public final ObcClient obc = new ObcClient(bluetooth, DeviceClient.GAME_THREAD);
	public final TrainerFeedback trainerFeedback = new TrainerFeedback(this);
	public final RideRecorder rideRecorder = new RideRecorder(this);
	public final VehicleRegistry vehicles = new VehicleRegistry();
	private FitnessControls controls = FitnessControls.NONE;
	private VehicleTelemetry telemetry;
	private final VehicleBinding binding =
			new VehicleBinding(
					() -> {
						telemetry = null;
						controls = FitnessControls.NONE;
						steering = 0;
						rideRecorder.interrupt();
						trainerFeedback.neutral();
					});
	private long ticks;
	private double steering;

	private FitnessRuntime() {}

	public static FitnessRuntime get() {
		if (instance == null) instance = new FitnessRuntime();
		return instance;
	}

	public FitnessControls controls() {
		return controls;
	}

	public VehicleTelemetry telemetry() {
		return telemetry;
	}

	public Object vehicleIdentity() {
		return binding.identity();
	}

	public void tick(Minecraft client) {
		bluetooth.tick();
		if (client.level != null) {
			ftms.tick(config);
			powerMeter.tick(config);
			heartRate.tick(config);
			obc.tick(config);
		}
		ObcMenuNavigation.tick(client, obc);
		TrainerTelemetry trainer = ftms.freshTelemetry();
		Integer power = PowerPreference.watts(powerMeter.freshReading(), trainer);
		Integer heart = HeartRatePreference.bpm(heartRate.freshReading(), trainer);
		int shifts = obc.controls().consumeShifts();
		while (FitnessKeys.SHIFT_UP.consumeClick()) shifts++;
		while (FitnessKeys.SHIFT_DOWN.consumeClick()) shifts--;
		int gear = obc.controls().consumeGearSet();
		double target = obc.isConnected() ? obc.controls().steering() : 0;
		boolean returning =
				Math.abs(target) < Math.abs(steering)
						|| Math.signum(target) != Math.signum(steering);
		steering +=
				Math.max(
						-(returning ? .45 : .3), Math.min(returning ? .45 : .3, target - steering));
		FitnessControls sampledControls =
				new FitnessControls(
						++ticks,
						power == null ? null : power.doubleValue(),
						trainer == null ? null : trainer.cadenceRpm(),
						heart,
						steering,
						obc.isConnected() ? obc.controls().brake() : 0,
						shifts,
						gear > 0 ? gear : null);
		Entity nextVehicle =
				client.player == null || client.level == null || client.isPaused()
						? null
						: client.player.getVehicle();
		VehicleAdapter next = null;
		try {
			if (nextVehicle != null)
				next = vehicles.select(a -> a.supports(client.player, nextVehicle));
			binding.select(nextVehicle, next);
			controls = sampledControls;
			if (next != null) {
				next.applyControls(client.player, nextVehicle, controls);
				telemetry =
						java.util.Objects.requireNonNull(next.sample(client.player, nextVehicle));
			}
		} catch (RuntimeException e) {
			LOGGER.warn("Fitness vehicle adapter failed; clearing controls and feedback", e);
			deactivate();
		}
		if (binding.adapter() == null) {
			controls = FitnessControls.NONE;
			steering = 0;
		}
		trainerFeedback.tick(client);
		rideRecorder.tick(client);
	}

	public void deactivate() {
		try {
			binding.clear();
		} catch (RuntimeException e) {
			LOGGER.warn("Fitness adapter cleanup failed", e);
		}
	}

	public void finishRide() {
		deactivate();
		saveRide();
	}

	public void saveRide() {
		if (rideRecorder.isRecording()) rideRecorder.stop();
		RideRecording ride = rideRecorder.lastRide();
		if (ride == null || ride.isEmpty() || rideRecorder.lastRideSaved()) return;
		try {
			var file = RideExport.writeFit(ride, config);
			rideRecorder.markLastRideSaved();
			LOGGER.info("Saved the unsaved ride to {}", file);
		} catch (IOException e) {
			LOGGER.warn("Could not save the unsaved ride", e);
		}
	}

	public void shutdown() {
		trainerFeedback.shutdown();
		finishRide();
		config.save();
		obc.shutdown();
		bluetooth.shutdown();
	}
}
