package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.client.ftms.TrainerState;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import net.minecraft.network.chat.Component;

/** One-line status texts for the trainer and the OpenBikeControl device, shared by the settings pages. */
public final class FitnessStatus {
	private FitnessStatus() {
	}

	public static Component trainerStatus(FitnessContext ctx) {
		String name = ctx.ftms.deviceName();
		if (name.isEmpty()) {
			name = ctx.config.preferredTrainerName;
		}
		if (name.isEmpty()) {
			return Component.translatable("voxelvelo.config.trainer.none");
		}
		TrainerState state = ctx.ftms.state();
		Component line = Component.translatable("voxelvelo.config.trainer", name, Component.translatable(stateKey(state)));
		String message = ctx.ftms.message();
		return state != TrainerState.CONNECTED && !message.isEmpty() ? line.copy().append(" - " + message) : line;
	}

	public static Component obcStatus(FitnessContext ctx) {
		String name = ctx.obc.deviceName();
		if (name.isEmpty()) {
			name = ctx.config.obcPreferredName;
		}
		if (name.isEmpty()) {
			return Component.translatable("voxelvelo.config.obc.none");
		}
		TrainerState state = ctx.obc.state();
		Component line = Component.translatable("voxelvelo.config.obc.status", name, Component.translatable(stateKey(state)));
		String message = ctx.obc.message();
		return state != TrainerState.CONNECTED && !message.isEmpty() ? line.copy().append(" - " + message) : line;
	}

	public static Component telemetryLine(FitnessContext ctx) {
		TrainerTelemetry telemetry = ctx.ftms.freshTelemetry();
		if (telemetry == null) {
			return Component.translatable("voxelvelo.config.telemetry", "--", "--", "--");
		}
		String cadence = telemetry.cadenceRpm() == null ? "--" : String.valueOf(Math.round(telemetry.cadenceRpm()));
		String heart = telemetry.heartRate() == null ? "--" : String.valueOf(telemetry.heartRate());
		return Component.translatable("voxelvelo.config.telemetry", telemetry.powerWatts(), cadence, heart);
	}

	private static String stateKey(TrainerState state) {
		return switch (state) {
			case CONNECTED -> "voxelvelo.config.trainer.connected";
			case CONNECTING -> "voxelvelo.config.trainer.connecting";
			case DISCONNECTED -> "voxelvelo.config.trainer.disconnected";
		};
	}
}
