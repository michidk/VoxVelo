package dev.michidk.voxvelo.bikes.bike;

import net.minecraft.util.Mth;

/**
 * A damped spring driven by the vertical acceleration of the bike. Climbing a step or landing makes the body sag
 * and then rebound; a stiff road bike barely moves and settles at once, a mountain bike sags further and rings
 * for longer. Climbing also settles the body a little lower. Rendering only, the server never runs it.
 */
final class BikeSuspension {
	private double offset;
	private double offsetO;
	private double velocity;
	private double lastY;
	private double lastVerticalSpeed;
	private boolean hasLastY;

	/**
	 * @param y         current height of the bike
	 * @param gradient  slope along the heading (uphill compresses the rest position)
	 * @param softness  the type's suspension, 0 (rigid) .. 1 (long travel)
	 */
	void tick(double y, double gradient, float softness) {
		this.offsetO = this.offset;
		double verticalSpeed = this.hasLastY ? Mth.clamp(y - this.lastY, -1.0, 1.0) : 0.0;
		double lift = verticalSpeed - this.lastVerticalSpeed;
		this.lastY = y;
		this.lastVerticalSpeed = verticalSpeed;
		this.hasLastY = true;

		double omega = Mth.lerp(softness, 0.75F, 0.42F);
		double damping = Mth.lerp(softness, 0.8F, 0.2F);
		double travel = 0.04 + 0.16 * softness;
		double rest = -Math.max(0.0, gradient) * softness * 0.12;
		this.velocity += -omega * omega * (this.offset - rest) - 2.0 * damping * omega * this.velocity
			- lift * (0.25 + 0.75 * softness);
		this.offset += this.velocity;
		if (Math.abs(this.offset) > travel) {
			this.offset = Math.signum(this.offset) * travel;
			this.velocity *= -0.3;
		}
	}

	/** Vertical offset of the body in blocks at the latest tick (negative is compressed). */
	double offset() {
		return this.offset;
	}

	float offset(float partialTick) {
		return (float) Mth.lerp(partialTick, this.offsetO, this.offset);
	}
}
