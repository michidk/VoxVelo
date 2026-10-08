package dev.michidk.voxvelo.fitnesslib.client.heartrate;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BleServiceClient;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothManager;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/** Maintains an independent BLE Heart Rate Service connection and its latest heart rate. */
public final class HeartRateClient extends BleServiceClient {
	private static final BleProfile PROFILE = new BleProfile(HeartRate.SERVICE, HeartRate.MEASUREMENT, "voxvelo_fitness_lib.connection.service.heart_rate");

	private @Nullable HeartRateReading reading;

	public HeartRateClient(BluetoothManager bluetooth, Executor gameThread) {
		super(bluetooth, gameThread, "heart rate sensor", PROFILE);
	}

	public @Nullable HeartRateReading freshReading() {
		HeartRateReading current = this.reading;
		return this.isConnected() && current != null && current.isFresh(System.currentTimeMillis()) ? current : null;
	}

	@Override
	public void tick(FitnessConfig config) {
		this.autoConnect(config.autoConnectHeartRate, config.preferredHeartRateId, config.preferredHeartRateName);
	}

	@Override
	protected void onData(byte[] data) {
		HeartRateMeasurement parsed = HeartRateMeasurement.parse(data);
		if (parsed != null) {
			this.reading = HeartRateReading.from(parsed, System.currentTimeMillis());
		}
	}

	@Override
	protected void onTeardown() {
		this.reading = null;
	}
}
