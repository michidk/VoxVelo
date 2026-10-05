package dev.michidk.voxelvelo.client.pose;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import dev.michidk.voxelvelo.bike.BikeEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The seated, pedaling pose of a player riding a bike: hands forward on the bars, legs following the cranks.
 * The pedal phase is read from the same GeckoLib controller that turns the crank, so legs and pedals stay
 * in step for every player who sees the bike.
 */
public final class BikeRiderPose {
	/** Carried from the entity into the player render state (see the AvatarRenderer mixin). */
	public static final RenderStateDataKey<BikeRiderPose> KEY = RenderStateDataKey.create();

	private static final float ARM_REACH = -1.25F;
	private static final float ARM_SPREAD = 0.1F;
	private static final float THIGH_BASE = -1.05F;
	private static final float THIGH_SWING = 0.45F;

	/** Position in the crank revolution, 0..1; 0 is the right pedal at the bottom. */
	private final float pedalPhase;

	private BikeRiderPose(float pedalPhase) {
		this.pedalPhase = pedalPhase;
	}

	/** The pose for a passenger of the given vehicle, or null if the vehicle is not a bike. */
	public static @Nullable BikeRiderPose of(@Nullable Entity vehicle) {
		if (!(vehicle instanceof BikeEntity bike)) {
			return null;
		}
		AnimatableManager<BikeEntity> manager = bike.getAnimatableInstanceCache().<BikeEntity>getManagerForId(bike.getId());
		AnimationController<BikeEntity> drive = manager.getAnimationControllers().get("drive");
		double time = drive == null ? 0.0 : drive.getCurrentAnimationTime();
		return new BikeRiderPose((float) (time - Math.floor(time)));
	}

	/** Overrides the limb rotations of the humanoid model. Called after the vanilla pose has been computed. */
	public void apply(ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
		float swing = Mth.cos(this.pedalPhase * Mth.TWO_PI) * THIGH_SWING;
		rightLeg.xRot = THIGH_BASE + swing;
		leftLeg.xRot = THIGH_BASE - swing;
		rightLeg.yRot = 0.0F;
		leftLeg.yRot = 0.0F;
		rightLeg.zRot = 0.0F;
		leftLeg.zRot = 0.0F;

		rightArm.xRot = ARM_REACH;
		leftArm.xRot = ARM_REACH;
		rightArm.yRot = 0.0F;
		leftArm.yRot = 0.0F;
		rightArm.zRot = ARM_SPREAD;
		leftArm.zRot = -ARM_SPREAD;
	}
}
