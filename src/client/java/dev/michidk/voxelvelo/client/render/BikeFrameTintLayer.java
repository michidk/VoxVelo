package dev.michidk.voxelvelo.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** Replaces just the frame's cube pass. Children and all other bones keep their original colors. */
public final class BikeFrameTintLayer extends CustomBoneTextureGeoLayer<BikeEntity, Void, EntityRenderState> {
    public BikeFrameTintLayer(BikeGeoRenderer renderer) {
        super(renderer, "frame_colored", VoxelVelo.id("textures/entity/bicycle_gravel.png"));
    }

    @Override protected Identifier getTextureResource(EntityRenderState state) {
        return renderer.getTextureLocation(state);
    }

    @Override protected void renderBone(RenderPassInfo<EntityRenderState> info, GeoBone bone, SubmitNodeCollector collector) {
        if (!(bone instanceof CuboidGeoBone cuboid)) return;
        var renderType = getRenderType(info.renderState(), getTextureResource(info.renderState()));
        if (renderType == null) return;
        int color = 0xFF000000 | info.getOrDefaultGeckolibData(BikeDataTickets.FRAME_COLOR, 0xFFFFFF);
        int light = info.packedLight(), overlay = info.packedOverlay();
        collector.submitCustomGeometry(info.poseStack(), renderType, (pose, vertices) -> {
            PoseStack stack = new PoseStack();
            stack.last().set(pose);
            bone.translateAwayFromPivotPoint(stack);
            for (var cube : cuboid.cubes) {
                stack.pushPose();
                renderCube(cube, stack, vertices, light, overlay, color, 1, 1);
                stack.popPose();
            }
        });
    }
}
