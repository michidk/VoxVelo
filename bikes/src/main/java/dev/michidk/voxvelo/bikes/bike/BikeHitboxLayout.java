package dev.michidk.voxvelo.bikes.bike;

import net.minecraft.world.phys.Vec3;

/** The small boxes that approximate the long, narrow footprint of a bicycle as it turns. */
public final class BikeHitboxLayout {
	public enum Part {
		FRONT_WHEEL(0.72, 0.38F, 0.78F),
		FRONT_FRAME(0.36, 0.42F, 0.88F),
		FRAME(0.0, 0.48F, 0.94F),
		REAR_FRAME(-0.36, 0.42F, 0.88F),
		REAR_WHEEL(-0.72, 0.38F, 0.78F);

		private final double forward;
		private final float width;
		private final float height;

		Part(double forward, float width, float height) {
			this.forward = forward;
			this.width = width;
			this.height = height;
		}

		public double forward() {
			return this.forward;
		}

		public float width() {
			return this.width;
		}

		public float height() {
			return this.height;
		}
	}

	private BikeHitboxLayout() {
	}

	/** Positions a part along the bike's forward axis. Minecraft yaw zero points toward positive Z. */
	public static Vec3 position(Vec3 origin, float yawDegrees, Part part) {
		double yaw = Math.toRadians(yawDegrees);
		return origin.add(-Math.sin(yaw) * part.forward(), 0.0, Math.cos(yaw) * part.forward());
	}
}
