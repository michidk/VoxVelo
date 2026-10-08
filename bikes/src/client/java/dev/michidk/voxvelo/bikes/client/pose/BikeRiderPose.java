package dev.michidk.voxvelo.bikes.client.pose;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeRig;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The seated, pedaling pose of a player riding a bike: the torso leans forward from the hip, the hands reach the
 * grips of that particular bike and the legs follow the pedals. The pedal phase is read from the same GeckoLib
 * controller that turns the crank, so legs and pedals stay in step for every player who sees the bike.
 *
 * <p>Lengths are in blocks (1 model pixel is 1/16). The player model has rigid limbs, so the arms and legs are
 * aimed at the grips and pedals rather than reaching them exactly.
 */
public final class BikeRiderPose {
	/** Carried from the entity into the player render state (see the AvatarRenderer mixin). */
	public static final RenderStateDataKey<BikeRiderPose> KEY = RenderStateDataKey.create();

	/** Forward lean of the torso, radians. */
	private static final float LEAN = 0.62F;
	private static final float HIP_Y = 12.0F;
	private static final float SHOULDER_UP = 10.0F;
	/** Half the distance between the shoulders of the player model, in blocks. */
	private static final double SHOULDER_HALF_WIDTH = 5.0 / 16.0;
	private static final double ARM_LENGTH = 12.0 / 16.0;
	/** The cranks are short; the legs swing this many times more than the pedals so the pedaling is visible. */
	private static final double LEG_SWING_GAIN = 4.0;
	private static final float MAX_LEG_ANGLE = 1.5F;

	private final BikeRig rig;
	/** Position in the crank revolution, 0..1. */
	private final float pedalPhase;

	private BikeRiderPose(BikeRig rig, float pedalPhase) {
		this.rig = rig;
		this.pedalPhase = pedalPhase;
	}

	/** The pose for a passenger of the given vehicle, or null if the vehicle is not a bike. */
	public static @Nullable BikeRiderPose of(@Nullable Entity vehicle) {
		if (!(vehicle instanceof BikeEntity bike)) {
			return null;
		}
		AnimatableManager<BikeEntity> manager = bike.getAnimatableInstanceCache().<BikeEntity>getManagerForId(bike.getId());
		AnimationController<BikeEntity> drive = manager.getAnimationControllers().get(BikeEntity.DRIVE_CONTROLLER);
		double time = drive == null ? 0.0 : drive.getCurrentAnimationTime();
		return new BikeRiderPose(bike.rig(), (float) (time - Math.floor(time)));
	}

	/** Overrides the body parts of the humanoid model. Called after the vanilla pose has been computed. */
	public void apply(ModelPart body, ModelPart head, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
		BikeRig rig = this.rig;
		float sin = Mth.sin(LEAN);
		float cos = Mth.cos(LEAN);

		// Lean the upper body about the hip: parts above the hip swing forward (-Z) and down.
		leanAboutHip(body, sin, cos);
		leanAboutHip(head, sin, cos);
		body.xRot = LEAN;

		// Shoulder after the lean, relative to the hip, in blocks.
		double shoulderUp = SHOULDER_UP / 16.0 * cos;
		double shoulderForward = SHOULDER_UP / 16.0 * sin;
		rightArm.y = HIP_Y - SHOULDER_UP * cos;
		leftArm.y = rightArm.y;
		rightArm.z = -SHOULDER_UP * sin;
		leftArm.z = rightArm.z;

		double reachForward = rig.gripForward() - shoulderForward;
		double reachDown = shoulderUp - rig.gripHeight();
		float armForward = (float) Math.atan2(reachForward, reachDown);
		double reach = Math.hypot(reachForward, reachDown);
		// The hands come together toward the bars: the grips are closer together than the shoulders.
		float armIn = (float) Math.atan2(SHOULDER_HALF_WIDTH - rig.gripX() * BikeRig.MODEL_SCALE / 16.0, Math.max(0.2, Math.min(reach, ARM_LENGTH)));
		rightArm.xRot = -armForward;
		leftArm.xRot = -armForward;
		rightArm.yRot = 0.0F;
		leftArm.yRot = 0.0F;
		rightArm.zRot = -armIn;
		leftArm.zRot = armIn;

		// The legs hang from the hip, which stays on the saddle. Forward pedaling turns the crank so the angle
		// from straight down toward the front decreases over time; the left pedal is opposite the right.
		float psiRight = (float) rig.pedalRestAngle() - this.pedalPhase * Mth.TWO_PI;
		rightLeg.xRot = this.legAngle(rig, psiRight);
		leftLeg.xRot = this.legAngle(rig, psiRight + Mth.PI);
		rightLeg.yRot = 0.0F;
		leftLeg.yRot = 0.0F;
		rightLeg.zRot = 0.0F;
		leftLeg.zRot = 0.0F;
	}

	/** Moves a part that sits on the spine as if the torso had pivoted about the hip. */
	private static void leanAboutHip(ModelPart part, float sin, float cos) {
		float up = HIP_Y - part.y;
		part.y = HIP_Y - up * cos;
		part.z = -up * sin;
	}

	/** Thigh angle (negative is forward) that points the leg from the hip toward a pedal at the given crank angle. */
	private float legAngle(BikeRig rig, double psi) {
		double radius = rig.pedalRadiusBlocks() * LEG_SWING_GAIN;
		double forward = rig.crankForward() + radius * Math.sin(psi);
		double drop = rig.crankDrop() + radius * Math.cos(psi);
		return Mth.clamp((float) -Math.atan2(forward, drop), -MAX_LEG_ANGLE, MAX_LEG_ANGLE);
	}
}
