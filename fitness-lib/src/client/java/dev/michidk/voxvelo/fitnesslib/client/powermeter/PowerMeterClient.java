package dev.michidk.voxvelo.fitnesslib.client.powermeter;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BleServiceClient;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothManager;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/** Maintains an independent BLE Cycling Power Service connection and its latest watt reading. */
public final class PowerMeterClient extends BleServiceClient {
	private static final BleProfile PROFILE = new BleProfile(CyclingPower.SERVICE, CyclingPower.MEASUREMENT, "voxvelo_fitness_lib.connection.service.cycling_power");

	private @Nullable PowerMeterReading reading;

	public PowerMeterClient(BluetoothManager bluetooth, Executor gameThread) {
		super(bluetooth, gameThread, "power meter", PROFILE);
	}

	public @Nullable PowerMeterReading freshReading() {
		PowerMeterReading current = this.reading;
		return this.isConnected() && current != null && current.isFresh(System.currentTimeMillis()) ? current : null;
	}

	@Override
	public void tick(FitnessConfig config) {
		this.autoConnect(config.autoConnectPowerMeter, config.preferredPowerMeterId, config.preferredPowerMeterName);
	}

	@Override
	protected void onData(byte[] data) {
		CyclingPowerMeasurement parsed = CyclingPowerMeasurement.parse(data);
		if (parsed != null) {
			this.reading = PowerMeterReading.from(parsed, System.currentTimeMillis());
		}
	}

	@Override
	protected void onTeardown() {
		this.reading = null;
	}
}
