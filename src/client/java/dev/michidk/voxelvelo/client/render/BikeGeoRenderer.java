package dev.michidk.voxelvelo.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.BikeRig;
import dev.michidk.voxelvelo.bike.BikePhysics;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;

/**
 * Renders bikes with GeckoLib. Keyframed animation covers the crank and pedals; the wheels and the
 * steering are driven from the synced physics state, because their angles depend continuously on
 * the distance travelled and the current speed.
 */
public class BikeGeoRenderer extends GeoEntityRenderer<BikeEntity, EntityRenderState> {
	public BikeGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new BikeGeoModel());
		this.shadowRadius = 0.5F;
        withRenderLayer(new BikeFrameTintLayer(this));
		this.withScale(BikeRig.MODEL_SCALE);
	}

	@Override
	public void extractRenderState(BikeEntity bike, EntityRenderState state, float partialTick) {
		super.extractRenderState(bike, state, partialTick);
		state.addGeckolibData(BikeDataTickets.FRAME_COLOR, bike.getFrameColor());
        state.addGeckolibData(BikeDataTickets.COMPONENTS, bike.getComponentMask());
		float steer = bike.getSteering() * (float) BikePhysics.maxSteerAngle(bike.getSpeed(), bike.getBikeType().params());
		state.addGeckolibData(BikeDataTickets.BIKE_TYPE, bike.getBikeType());
		state.addGeckolibData(BikeDataTickets.WHEEL_ANGLE, bike.getWheelAngle(partialTick));
		state.addGeckolibData(BikeDataTickets.STEER_ANGLE, steer);
		state.addGeckolibData(BikeDataTickets.SUSPENSION, bike.getSuspensionOffset(partialTick));
		state.addGeckolibData(BikeDataTickets.HURT_TIME, bike.getHurtTime() - partialTick);
		state.addGeckolibData(BikeDataTickets.HURT_DIR, bike.getHurtDir());
		state.addGeckolibData(BikeDataTickets.DAMAGE, Math.max(bike.getDamage() - partialTick, 0.0F));
	}

	/** The default entity yaw is not interpolated between ticks, which would make turning stutter at 20 Hz. */
	@Override
	protected float calculateYRot(BikeEntity bike, float yHeadRot, float partialTick) {
		return bike.getYRot(partialTick);
	}

	@Override
	protected void applyRotations(RenderPassInfo<EntityRenderState> info, PoseStack poseStack, float nativeScale) {
		super.applyRotations(info, poseStack, nativeScale);
		poseStack.translate(0.0F, info.getOrDefaultGeckolibData(BikeDataTickets.SUSPENSION, 0.0F), 0.0F);
		float hurt = info.getOrDefaultGeckolibData(BikeDataTickets.HURT_TIME, 0.0F);
		if (hurt > 0.0F) {
			float damage = info.getOrDefaultGeckolibData(BikeDataTickets.DAMAGE, 0.0F);
			int dir = info.getOrDefaultGeckolibData(BikeDataTickets.HURT_DIR, 1);
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(hurt) * hurt * damage / 10.0F * dir);
		}
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> info, BoneSnapshots snapshots) {
		int parts = info.getOrDefaultGeckolibData(BikeDataTickets.COMPONENTS, 31);
        String[] names = {"front_wheel", "rear_wheel", "handlebar", "saddle", "crank"};
        for (int i = 0; i < names.length; i++) {
            boolean missing = (parts & (1 << i)) == 0;
            snapshots.ifPresent(names[i], bone -> { bone.skipRender(missing); bone.skipChildrenRender(missing); });
        }
        snapshots.ifPresent("left_pedal", bone -> bone.skipRender((parts & 16) == 0));
        snapshots.ifPresent("right_pedal", bone -> bone.skipRender((parts & 16) == 0));
        float wheel = info.getOrDefaultGeckolibData(BikeDataTickets.WHEEL_ANGLE, 0.0F);
		float steer = info.getOrDefaultGeckolibData(BikeDataTickets.STEER_ANGLE, 0.0F);
		snapshots.ifPresent("rear_wheel", bone -> bone.setRotX(-wheel));
		snapshots.ifPresent("front_wheel", bone -> bone.setRotX(-wheel));
		// The fork rests tilted along the steering axis (its base rotation), so steering is a plain turn about its local Y.
		// GeckoLib runtime rotations are the mirror image of model-space ones (wheel and steering both need a negated angle).
		snapshots.ifPresent("fork", bone -> bone.setRotY(-steer));
		// The tube bones are drawn tinted by the frame layer instead, see BikeFrameTintLayer.
		for (String tube : BikeFrameTintLayer.TUBES) {
			snapshots.ifPresent(tube, bone -> bone.skipRender(true));
		}
	}
}
