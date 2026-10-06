package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.client.ftms.FtmsClient;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import dev.michidk.voxelvelo.client.fitness.PowerPreference;
import dev.michidk.voxelvelo.client.powermeter.PowerMeterClient;
import dev.michidk.voxelvelo.client.powermeter.PowerMeterReading;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;

/**
 * Trainer power as rider propulsion. Only power is used; the speed a trainer reports is never fed into
 * the simulation. If the trainer disconnects or goes silent the power is zero and the bike coasts.
 */
public final class FtmsTrainerInput implements BikeInputSource {
	private final FtmsClient ftms;
	private final PowerMeterClient powerMeter;

	public FtmsTrainerInput(FtmsClient ftms, PowerMeterClient powerMeter) {
		this.ftms = ftms;
		this.powerMeter = powerMeter;
	}

	@Override
	public String id() {
		return "ftms";
	}

	@Override
	public boolean supports(ControlChannel channel) {
		return channel == ControlChannel.PROPULSION;
	}

	@Override
	public String displayName() {
		return "Trainer / power meter";
	}

	@Override
	public boolean isActive() {
		return this.ftms.isConnected() || this.powerMeter.isConnected();
	}

	@Override
	public void tick(Minecraft client) {
	}

	@Override
	public void reset() {
	}

	@Override
	public OptionalDouble propulsion() {
		PowerMeterReading meter = this.powerMeter.freshReading();
		TrainerTelemetry telemetry = this.ftms.freshTelemetry();
		Integer watts = PowerPreference.watts(meter, telemetry);
		return OptionalDouble.of(watts == null ? 0.0 : watts);
	}

	@Override
	public OptionalDouble steering() {
		return OptionalDouble.empty();
	}

	@Override
	public OptionalDouble brake() {
		return OptionalDouble.empty();
	}

	@Override
	public int consumeGearShift() {
		return 0;
	}
}
