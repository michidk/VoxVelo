package dev.michidk.voxvelo.bikes.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws the frame in the bike's frame color: the {@code frame_colored} bone and the tubes under it. The fork, cockpit,
 * wheels, saddle and drivetrain are not its children and keep their own colors.
 */
public final class BikeFrameTintLayer extends CustomBoneTextureGeoLayer<BikeEntity, Void, EntityRenderState> {
	/** Bones under frame_colored; the renderer hides them in the normal pass because this layer draws them tinted. */
	public static final String[] TUBES = {"top_tube", "down_tube", "seat_tube", "rear_stays"};

	public BikeFrameTintLayer(BikeGeoRenderer renderer) {
		// The texture is the bike's own, see getTextureResource; the one given here is only a placeholder.
		super(renderer, "frame_colored", BikeGeoModel.texture(BikeType.GRAVEL));
	}

	@Override protected Identifier getTextureResource(EntityRenderState state) {
		return renderer.getTextureLocation(state);
	}

	@Override protected void renderBone(RenderPassInfo<EntityRenderState> info, GeoBone bone, SubmitNodeCollector collector) {
		var renderType = getRenderType(info.renderState(), getTextureResource(info.renderState()));
		if (renderType == null) return;
		int color = 0xFF000000 | info.getOrDefaultGeckolibData(BikeDataTickets.FRAME_COLOR, 0xFFFFFF);
		int light = info.packedLight(), overlay = info.packedOverlay();
		collector.submitCustomGeometry(info.poseStack(), renderType, (pose, vertices) -> {
			PoseStack stack = new PoseStack();
			stack.last().set(pose);
			bone.translateAwayFromPivotPoint(stack);
			BoneGeometry.draw(this, bone, stack, vertices, light, overlay, color);
		});
	}
}
