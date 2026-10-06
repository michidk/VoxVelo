package dev.michidk.voxelvelo.client.render;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.michidk.voxelvelo.bike.BikeComponents;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.BikeType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Draws a part that came from another bike: a gravel wheel on a race frame, flat bars on a gravel frame. The frame's
 * own bone is hidden and the same bone of the other bike's model is drawn in its place, with that model's texture.
 *
 * <p>The other model's bone is moved so its pivot sits on this model's pivot: wheels share their axle, handlebars the
 * top of the head tube. The bone's pose (wheel spin, steering) is this model's, so the part moves like the original.
 */
public final class BikePartSwapLayer extends CustomBoneTextureGeoLayer<BikeEntity, Void, EntityRenderState> {
    /** Bones that can come from another bike, indexed by component slot (front wheel, rear wheel, handlebars). */
    public static final String[] BONES = {"front_wheel", "rear_wheel", "handlebar"};

    private final int slot;

    public BikePartSwapLayer(BikeGeoRenderer renderer, int slot) {
        super(renderer, BONES[slot], BikeGeoModel.texture(BikeType.GRAVEL));
        this.slot = slot;
    }

    /** The bike whose model the part in this slot is drawn from; the frame's own type for its stock parts. */
    public static BikeType donor(GeoRenderState state, int slot) {
        BikeType frame = state.getOrDefaultGeckolibData(BikeDataTickets.BIKE_TYPE, BikeType.GRAVEL);
        int setup = state.getOrDefaultGeckolibData(BikeDataTickets.SETUP, BikeComponents.stockSetup(frame));
        return switch (slot) {
            case BikeComponents.FRONT -> BikeComponents.frontTread(setup).origin;
            case BikeComponents.REAR -> BikeComponents.rearTread(setup).origin;
            default -> {
                boolean flat = BikeComponents.flatBars(setup);
                // Race and gravel drop bars are the same, so only a change between drops and flats needs a swap.
                yield flat == (frame == BikeType.MOUNTAIN) ? frame : flat ? BikeType.MOUNTAIN : BikeType.GRAVEL;
            }
        };
    }

    /** Whether this slot holds a part from another bike, so the frame's own bone must not be drawn. */
    public static boolean swapped(GeoRenderState state, int slot) {
        int parts = state.getOrDefaultGeckolibData(BikeDataTickets.COMPONENTS, BikeComponents.ALL_PARTS_MASK);
        return (parts & BikeComponents.bit(slot)) != 0
                && donor(state, slot) != state.getOrDefaultGeckolibData(BikeDataTickets.BIKE_TYPE, BikeType.GRAVEL);
    }

    @Override public boolean shouldRenderBone(EntityRenderState state) {
        return swapped(state, slot);
    }

    @Override protected void renderBone(RenderPassInfo<EntityRenderState> info, GeoBone bone, SubmitNodeCollector collector) {
        BikeType donor = donor(info.renderState(), slot);
        GeoBone part = GeckoLibResources.getBakedModels().getModel(BikeGeoModel.model(donor)).getBone(boneName).orElse(null);
        var renderType = getRenderType(info.renderState(), BikeGeoModel.texture(donor));
        if (part == null || renderType == null) return;
        int light = info.packedLight(), overlay = info.packedOverlay(), color = info.renderColor();
        collector.submitCustomGeometry(info.poseStack(), renderType, (pose, vertices) -> {
            PoseStack stack = new PoseStack();
            stack.last().set(pose);
            // The pose is at this model's pivot; moving away from the other model's pivot lines the two up.
            part.translateAwayFromPivotPoint(stack);
            render(part, stack, vertices, light, overlay, color);
        });
    }

    private void render(GeoBone bone, PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        if (bone instanceof CuboidGeoBone cuboid) {
            for (var cube : cuboid.cubes) {
                stack.pushPose();
                renderCube(cube, stack, vertices, light, overlay, color, 1, 1);
                stack.popPose();
            }
        }
        for (GeoBone child : bone.children()) {
            render(child, stack, vertices, light, overlay, color);
        }
    }
}
