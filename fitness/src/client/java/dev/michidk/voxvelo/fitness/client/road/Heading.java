package dev.michidk.voxvelo.fitness.client.road;

/**
 * A unit direction on the ground, (fx, fz), with the direction to its right, (rx, rz). Minecraft yaw 0 faces +Z and
 * grows clockwise seen from above, so right of a heading is a quarter turn further.
 */
public record Heading(double fx, double fz) {
	/** The heading of a Minecraft yaw in degrees. */
	public static Heading ofYaw(double yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Heading(-Math.sin(yaw), Math.cos(yaw));
	}

	public double rx() {
		return -this.fz;
	}

	public double rz() {
		return this.fx;
	}
}
