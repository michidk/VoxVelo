package dev.michidk.voxvelo.fitnesslib.client.hud;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.ftms.TrainerSimulation;
import dev.michidk.voxvelo.fitnesslib.client.ftms.VirtualShifting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/** Shows requested road load, actual writes and confirmed FTMS replies separately. */
public final class TrainerDebugHud implements HudElement {
	private final FitnessRuntime ctx;

	public TrainerDebugHud(FitnessRuntime ctx) { this.ctx = ctx; }

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!this.ctx.config.trainerDebugHudEnabled || mc.player == null || mc.level == null) return;
		List<String> lines = new ArrayList<>();
		lines.add("Trainer debug | FTMS SIM (0x11)");
		lines.add(this.ctx.ftms.deviceName() + " | " + this.ctx.ftms.state());
		var vehicle = this.ctx.telemetry();
		lines.add("Resistance: " + (this.ctx.config.trainerResistanceEnabled ? "ON" : "OFF")
			+ " | riding: " + (vehicle != null ? "yes" : "no"));
		if (vehicle != null) {
			lines.add(format("Road: %+.2f%% | intensity %.2fx", vehicle.gradient() * 100, this.ctx.config.trainerResistanceIntensity));
			lines.add(format("Virtual gear: %.2f | range %s %.2f-%.2f | %s", vehicle.virtualGearRatio(), this.ctx.config.virtualGearRange,
				this.ctx.config.virtualGearRange.lowest, this.ctx.config.virtualGearRange.highest,
				this.ctx.config.virtualShiftingEnabled ? "ON" : "OFF"));
		}
		var telemetry = this.ctx.ftms.freshTelemetry();
		if (telemetry == null) lines.add("Telemetry: unavailable / stale");
		else {
			lines.add(format("Telemetry: %d W | %s rpm | %s km/h", telemetry.powerWatts(), value(telemetry.cadenceRpm()), value(telemetry.speedKmh())));
			if (vehicle != null && telemetry.cadenceRpm() != null) {
				lines.add(format("Virtual speed: %.2f m/s | reported resistance: %s",
					VirtualShifting.virtualSpeed(telemetry.cadenceRpm(), vehicle.virtualGearRatio(),
						this.ctx.config.trainerWheelCircumferenceM), telemetry.resistanceLevel() == null ? "n/a" : telemetry.resistanceLevel()));
			}
		}
		if (vehicle != null && this.ctx.config.virtualShiftingEnabled) {
			if (telemetry == null || telemetry.cadenceRpm() == null || !Double.isFinite(telemetry.cadenceRpm()))
				lines.add("Shifting inactive: no cadence | plain road target");
			else if (!VirtualShifting.telemetryAvailable(telemetry.cadenceRpm(), telemetry.speedKmh()))
				lines.add("Shifting inactive: no moving trainer speed | plain road target");
		}
		var control = this.ctx.ftms.control();
		if (control == null) lines.add("Control: no trainer connection");
		else {
			var debug = control.diagnostics();
			lines.add("Control: " + debug.status() + " | " + control.support());
			if (!debug.error().isEmpty()) lines.add("Error: " + debug.error());
			lines.add("Indications: " + (debug.indicationsReady() ? "subscribed" : "unavailable")
				+ " | control held: " + (debug.controlling() ? "yes" : "no"));
			lines.add("Requested: " + target(debug.requested()));
			lines.add("GATT written: " + target(debug.written()) + age(debug.writtenAt()));
			lines.add("FTMS accepted: " + target(debug.acknowledged()) + age(debug.acknowledgedAt()));
			lines.add(format("SIM writes: %d | accepted: %d | wind: 0 m/s, Cw 0.51", debug.writes(), debug.acknowledgements()));
			lines.add("TX: " + (debug.packet().isEmpty() ? "none" : debug.packet()));
			lines.add("RX: " + debug.response());
			if (debug.retryAt() > System.currentTimeMillis())
				lines.add(format("Retry in %.1f s", (debug.retryAt() - System.currentTimeMillis()) / 1000.0));
		}
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		int width = Math.max(80, Math.min(330, graphics.guiWidth() - 20));
		for (String line : lines) wrapped.addAll(mc.font.split(Component.literal(line), width));
		int count = Math.min(wrapped.size(), Math.max(1, (graphics.guiHeight() - 20) / 10));
		graphics.fill(4, 4, width + 12, 12 + count * 10, 0xC0000000);
		for (int i = 0; i < count; i++) graphics.text(mc.font, wrapped.get(i), 8, 8 + i * 10, i == 0 ? 0xFFFFD060 : 0xFFFFFFFF);
	}

	private static String target(TrainerSimulation.@Nullable Target target) {
		return target == null ? "none" : format("grade %+.2f%%, Crr %.4f", target.gradePercent(), target.crr());
	}

	private static String value(@Nullable Double value) { return value == null ? "n/a" : format("%.1f", value); }
	private static String age(long time) { return time == 0 ? "" : format(" (%.1fs ago)", Math.max(0, System.currentTimeMillis() - time) / 1000.0); }
	private static String format(String pattern, Object... args) { return String.format(Locale.ROOT, pattern, args); }
}
