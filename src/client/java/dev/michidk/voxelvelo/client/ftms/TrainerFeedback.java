package dev.michidk.voxelvelo.client.ftms;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.ClientContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Feeds the terrain of the bike the local player rides back to the trainer as resistance: uphill makes it
 * harder, downhill easier, sand and grass add rolling resistance. Runs from the client tick and never
 * blocks; all limits are in {@link TrainerSimulation}.
 *
 * <p>It only acts while a bike is ridden, the trainer is the propulsion source and is connected, and the
 * setting is on. When any of that stops, the trainer is returned to a flat road.
 */
public final class TrainerFeedback {
	private static final long MIN_INTERVAL_MILLIS = 750;
	/** Per-tick smoothing so single stair steps do not make the trainer jerk. */
	private static final double SMOOTHING = 0.35;
	private static final double GRADE_DEADBAND_PERCENT = 0.15;
	private static final long NEUTRAL_WAIT_ON_EXIT_MILLIS = 400;

	private final ClientContext ctx;
	private boolean applying;
	private boolean hasSmoothed;
	private double smoothedGrade;
	private long lastSentAt;
	private TrainerSimulation.Target lastSent = TrainerSimulation.Target.NEUTRAL;

	public TrainerFeedback(ClientContext ctx) {
		this.ctx = ctx;
	}

	public void tick(Minecraft client) {
		FtmsControl control = this.ctx.ftms.control();
		BikeEntity bike = this.riddenBike(client.player);
		boolean enabled = this.ctx.config.trainerResistanceEnabled
			&& this.ctx.ftms.isConnected()
			&& control != null
			&& this.ctx.inputManager.selected(dev.michidk.voxelvelo.client.input.ControlChannel.PROPULSION).id().equals("ftms");

		if (bike == null || !enabled) {
			if (this.applying && control != null) {
				control.neutral();
			}
			this.applying = false;
			this.hasSmoothed = false;
			return;
		}

		TrainerSimulation.Target raw = TrainerSimulation.target(
			bike.getGradient(), bike.getSurface(), bike.getBikeType().params(), this.ctx.config.trainerResistanceIntensity);
		this.smoothedGrade = this.hasSmoothed ? this.smoothedGrade + (raw.gradePercent() - this.smoothedGrade) * SMOOTHING : raw.gradePercent();
		this.hasSmoothed = true;
		TrainerSimulation.Target target = new TrainerSimulation.Target(this.smoothedGrade, raw.crr());

		long now = System.currentTimeMillis();
		boolean due = !this.applying || now - this.lastSentAt >= MIN_INTERVAL_MILLIS;
		boolean changed = Math.abs(target.gradePercent() - this.lastSent.gradePercent()) >= GRADE_DEADBAND_PERCENT
			|| Math.abs(target.crr() - this.lastSent.crr()) >= 0.0001;
		if (due && (changed || !this.applying)) {
			control.apply(target);
			this.lastSent = target;
			this.lastSentAt = now;
		}
		this.applying = true;
	}

	/** Best effort on game exit: leave the trainer on a flat road instead of the last hill. */
	public void shutdown() {
		FtmsControl control = this.ctx.ftms.control();
		if (this.applying && control != null) {
			control.neutral();
			try {
				Thread.sleep(NEUTRAL_WAIT_ON_EXIT_MILLIS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private BikeEntity riddenBike(LocalPlayer player) {
		return player != null && player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player ? bike : null;
	}
}
