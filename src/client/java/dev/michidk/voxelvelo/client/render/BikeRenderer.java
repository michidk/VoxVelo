package dev.michidk.voxelvelo.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.BikePhysics;
import dev.michidk.voxelvelo.bike.BikeType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class BikeRenderer extends EntityRenderer<BikeEntity, BikeRenderState> {
	private final Map<BikeType, BikeModel> models = new EnumMap<>(BikeType.class);
	private final Map<BikeType, Identifier> textures = new EnumMap<>(BikeType.class);

	public BikeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		for (BikeType type : BikeType.values()) {
			this.models.put(type, new BikeModel(context.bakeLayer(BikeModel.layer(type))));
			this.textures.put(type, VoxelVelo.id("textures/entity/bicycle_" + type.id + ".png"));
		}
	}

	@Override
	public BikeRenderState createRenderState() {
		return new BikeRenderState();
	}

	@Override
	public void extractRenderState(BikeEntity entity, BikeRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.type = entity.getBikeType();
		state.yRot = entity.getYRot(partialTick);
		state.steering = entity.getSteering();
		state.speed = entity.getSpeed();
		state.maxSteerAngle = (float) BikePhysics.maxSteerAngle(state.speed, state.type.params());
		state.wheelAngle = entity.getWheelAngle(partialTick);
		state.crankAngle = entity.getCrankAngle(partialTick);
		state.hurtTime = entity.getHurtTime() - partialTick;
		state.hurtDir = entity.getHurtDir();
		state.damageTime = Math.max(entity.getDamage() - partialTick, 0.0F);
	}

	@Override
	public void submit(BikeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// Model origin is the wheel axle height; the model faces -Z and uses +Y down.
		poseStack.translate(0.0F, 0.375F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		if (state.hurtTime > 0.0F) {
			poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir);
		}
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.models.get(state.type), state, poseStack, this.textures.get(state.type), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
