package dev.michidk.voxelvelo.client.fitness;

import dev.michidk.voxelvelo.client.ftms.TrainerTelemetry;
import dev.michidk.voxelvelo.client.hud.HudExtras;
import dev.michidk.voxelvelo.client.input.BikeInputSource;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** What the fitness integration adds to the riding HUD: trainer cadence and pulse, the smoothed slope, and the source dots. */
final class FitnessHudExtras implements HudExtras {
	private final FitnessContext ctx;

	FitnessHudExtras(FitnessContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public OptionalDouble cadenceRpm() {
		TrainerTelemetry telemetry = this.ctx.ftms.freshTelemetry();
		return telemetry != null && telemetry.cadenceRpm() != null ? OptionalDouble.of(telemetry.cadenceRpm()) : OptionalDouble.empty();
	}

	@Override
	public OptionalDouble gradient() {
		return this.ctx.rideTerrain.slope();
	}

	@Override
	public void addLines(List<Component> lines) {
		if (this.ctx.config.hudShowSources) {
			for (ControlChannel channel : ControlChannel.values()) {
				lines.add(this.sourceLine(channel));
			}
		}
	}

	private Component sourceLine(ControlChannel channel) {
		BikeInputSource source = this.ctx.inputManager.selected(channel);
		Component dot = Component.literal("●").withStyle(source.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED);
		Component label = Component.translatable("voxelvelo.sources." + channel.name().toLowerCase());
		return Component.empty().append(dot).append(" ").append(label).append(": ").append(source.displayName());
	}
}
