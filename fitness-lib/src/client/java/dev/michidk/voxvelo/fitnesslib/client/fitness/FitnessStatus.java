package dev.michidk.voxvelo.fitnesslib.client.fitness;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.DeviceClient;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerTelemetry;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.PowerMeterReading;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** One-line status texts for the trainer, power meter, heart rate sensor and OpenBikeControl device, shared by the settings pages. */
public final class FitnessStatus {
	private FitnessStatus() {
	}

	public static Component trainerStatus(FitnessRuntime ctx) {
		return deviceStatus("voxvelo_fitness_lib.config.trainer", "voxvelo_fitness_lib.config.trainer.none", ctx.ftms, ctx.config.preferredTrainerName);
	}

	public static Component obcStatus(FitnessRuntime ctx) {
		return deviceStatus("voxvelo_fitness_lib.config.obc.status", "voxvelo_fitness_lib.config.obc.none", ctx.obc, ctx.config.obcPreferredName);
	}

	public static Component powerMeterStatus(FitnessRuntime ctx) {
		return deviceStatus("voxvelo_fitness_lib.config.power_meter", "voxvelo_fitness_lib.config.power_meter.none", ctx.powerMeter, ctx.config.preferredPowerMeterName);
	}

	public static Component heartRateStatus(FitnessRuntime ctx) {
		return deviceStatus("voxvelo_fitness_lib.config.heart_rate", "voxvelo_fitness_lib.config.heart_rate.none", ctx.heartRate, ctx.config.preferredHeartRateName);
	}

	/** "Role: device (state)", followed by what is going on or went wrong while not connected. */
	private static Component deviceStatus(String lineKey, String noneKey, DeviceClient client, String savedName) {
		String name = client.deviceName().isEmpty() ? savedName : client.deviceName();
		if (name.isEmpty()) {
			return Component.translatable(noneKey);
		}
		ConnectionState state = client.state();
		Component line = Component.translatable(lineKey, name, stateText(state));
		Component message = client.message();
		return state != ConnectionState.CONNECTED && message != null ? Component.translatable("voxvelo_fitness_lib.connection.with_message", line, message) : line;
	}

	public static Component telemetryLine(FitnessRuntime ctx) {
		TrainerTelemetry telemetry = ctx.ftms.freshTelemetry();
		PowerMeterReading meter = ctx.powerMeter.freshReading();
		Integer power = PowerPreference.watts(meter, telemetry);
		Integer heartRate = HeartRatePreference.bpm(ctx.heartRate.freshReading(), telemetry);
		if (telemetry == null && power == null && heartRate == null) {
			return Component.translatable("voxvelo_fitness_lib.config.telemetry", "--", "--", "--");
		}
		String cadence = telemetry == null || telemetry.cadenceRpm() == null ? "--" : String.valueOf(Math.round(telemetry.cadenceRpm()));
		String heart = heartRate == null ? "--" : String.valueOf(heartRate);
		return Component.translatable("voxvelo_fitness_lib.config.telemetry", power == null ? "--" : power, cadence, heart);
	}

	/** The state of any device connection, such as "connected". */
	public static Component stateText(ConnectionState state) {
		return Component.translatable("voxvelo_fitness_lib.connection.state." + state.name().toLowerCase(Locale.ROOT));
	}
}
