package dev.michidk.voxelvelo.client.input;

import dev.michidk.voxelvelo.client.ftms.FtmsClient;
import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;

/**
 * Trainer power as rider propulsion. Only power is used; the speed a trainer reports is never fed into
 * the simulation. If the trainer disconnects or goes silent the power is zero and the bike coasts.
 */
public final class FtmsTrainerInput implements BikeInputSource {
	private final FtmsClient ftms;

	public FtmsTrainerInput(FtmsClient ftms) {
		this.ftms = ftms;
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
		return "FTMS trainer";
	}

	@Override
	public boolean isActive() {
		return this.ftms.isConnected();
	}

	@Override
	public void tick(Minecraft client) {
	}

	@Override
	public void reset() {
	}

	@Override
	public OptionalDouble propulsion() {
		TrainerTelemetry telemetry = this.ftms.freshTelemetry();
		return OptionalDouble.of(telemetry == null ? 0.0 : telemetry.powerWatts());
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
