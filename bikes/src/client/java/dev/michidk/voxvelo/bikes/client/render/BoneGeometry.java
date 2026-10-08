package dev.michidk.voxvelo.bikes.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Draws a bone subtree in one color, for the layers that draw their bone themselves instead of the normal pass. */
final class BoneGeometry {
	private BoneGeometry() {
	}

	/** The cubes of {@code bone}, from the pose in {@code stack}, then each child in its own pose relative to it. */
	static void draw(CustomBoneTextureGeoLayer<?, ?, ?> layer, GeoBone bone, PoseStack stack, VertexConsumer vertices, int light,
		int overlay, int color) {
		if (bone instanceof CuboidGeoBone cuboid) {
			for (var cube : cuboid.cubes) {
				stack.pushPose();
				layer.renderCube(cube, stack, vertices, light, overlay, color, 1, 1);
				stack.popPose();
			}
		}
		for (GeoBone child : bone.children()) {
			stack.pushPose();
			RenderUtil.prepMatrixForBone(stack, child);
			draw(layer, child, stack, vertices, light, overlay, color);
			stack.popPose();
		}
	}
}
