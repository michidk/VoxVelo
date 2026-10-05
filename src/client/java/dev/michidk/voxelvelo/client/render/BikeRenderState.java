package dev.michidk.voxelvelo.client.render;

import dev.michidk.voxelvelo.bike.BikeType;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BikeRenderState extends EntityRenderState {
	public BikeType type = BikeType.GRAVEL;
	public float yRot;
	/** -1 (left) .. +1 (right) */
	public float steering;
	/** Front wheel lock in radians at the current speed for this bike type. */
	public float maxSteerAngle;
	public float speed;
	public float wheelAngle;
	public float crankAngle;
	public float hurtTime;
	public int hurtDir;
	public float damageTime;
}
