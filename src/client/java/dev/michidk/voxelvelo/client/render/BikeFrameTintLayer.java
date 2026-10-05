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

/**
 * Draws the frame in the bike's frame color: the {@code frame_colored} bone and the tubes under it. The fork, cockpit,
 * wheels, saddle and drivetrain are not its children and keep their own colors.
 */
public final class BikeFrameTintLayer extends CustomBoneTextureGeoLayer<BikeEntity, Void, EntityRenderState> {
    /** Bones under frame_colored; the renderer hides them in the normal pass because this layer draws them tinted. */
    public static final String[] TUBES = {"top_tube", "down_tube", "seat_tube", "rear_stays"};

    public BikeFrameTintLayer(BikeGeoRenderer renderer) {
        super(renderer, "frame_colored", VoxelVelo.id("textures/entity/bikes/gravel_bike.png"));
    }

    @Override protected Identifier getTextureResource(EntityRenderState state) {
        return renderer.getTextureLocation(state);
    }

    private void renderTinted(GeoBone bone, PoseStack stack, com.mojang.blaze3d.vertex.VertexConsumer vertices, int light, int overlay, int color) {
        if (bone instanceof CuboidGeoBone cuboid) {
            for (var cube : cuboid.cubes) {
                stack.pushPose();
                renderCube(cube, stack, vertices, light, overlay, color, 1, 1);
                stack.popPose();
            }
        }
        for (GeoBone child : bone.children()) {
            renderTinted(child, stack, vertices, light, overlay, color);
        }
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
            // The frame bones all pivot at the origin and are not rotated, so they share one pose.
            renderTinted(bone, stack, vertices, light, overlay, color);
        });
    }
}
