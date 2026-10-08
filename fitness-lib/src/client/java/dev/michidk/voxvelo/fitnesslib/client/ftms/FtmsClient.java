package dev.michidk.voxvelo.fitnesslib.client.ftms;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BleServiceClient;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothConnection;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothManager;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * Connects to one FTMS trainer and exposes its latest TrainerTelemetry and its resistance control. Never blocks the
 * caller; the connection life cycle is in {@link BleServiceClient}.
 */
public final class FtmsClient extends BleServiceClient {
	private static final BleProfile PROFILE = new BleProfile(Ftms.SERVICE, Ftms.INDOOR_BIKE_DATA, "voxvelo_fitness_lib.connection.service.ftms");

	private @Nullable TrainerTelemetry telemetry;
	private @Nullable FtmsControl control;

	public FtmsClient(BluetoothManager bluetooth, Executor gameThread) {
		super(bluetooth, gameThread, "trainer", PROFILE);
	}

	/** Resistance control of the connected trainer, or null when not connected. */
	public @Nullable FtmsControl control() {
		return this.control;
	}

	/** Latest telemetry, or null if the trainer is not connected or has gone silent. */
	public @Nullable TrainerTelemetry freshTelemetry() {
		TrainerTelemetry current = this.telemetry;
		return this.isConnected() && current != null && current.isFresh(System.currentTimeMillis()) ? current : null;
	}

	@Override
	public void tick(FitnessConfig config) {
		this.autoConnect(config.autoConnectTrainer, config.preferredTrainerId, config.preferredTrainerName);
	}

	@Override
	protected void onData(byte[] data) {
		IndoorBikeData parsed = IndoorBikeData.parse(data);
		if (parsed != null) {
			this.telemetry = TrainerTelemetry.merge(this.telemetry, parsed, System.currentTimeMillis());
		}
	}

	@Override
	protected void onConnected() {
		BluetoothConnection connection = this.connection();
		if (connection != null) {
			FtmsControl newControl = new FtmsControl(connection);
			this.control = newControl;
			newControl.initialise();
		}
	}

	/** Trainer power becomes zero at once. */
	@Override
	protected void onTeardown() {
		this.control = null;
		this.telemetry = null;
	}
}
