package dev.michidk.voxvelo.client.render;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.CustomBoneTextureGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.michidk.voxvelo.bike.BikeComponents;
import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.bike.BikeType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Composes a wheel or handlebar model onto a frame attachment bone. The frame models intentionally contain empty
 * attachment bones for the {@link #SLOTS}; the selected standalone component supplies the geometry and texture for
 * each installed part.
 *
 * <p>The component pivot is moved onto the frame's attachment pivot: wheels share their axle, handlebars the top of
 * the head tube. The attachment pose owns wheel spin and steering, so every component follows the host frame cleanly.
 */
public final class BikeComponentLayer extends CustomBoneTextureGeoLayer<BikeEntity, Void, EntityRenderState> {
	/** The slots whose part can come from another bike's model: the wheels and the handlebars. */
	public static final int[] SLOTS = {BikeComponents.FRONT, BikeComponents.REAR, BikeComponents.BARS};

	private final int slot;

	public BikeComponentLayer(BikeGeoRenderer renderer, int slot) {
		super(renderer, BikeGeoModel.bone(slot), BikeGeoModel.texture(BikeType.GRAVEL));
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
				// Road and gravel drop bars are the same, so only a change between drops and flats needs a swap.
				yield flat == (frame == BikeType.MOUNTAIN) ? frame : flat ? BikeType.MOUNTAIN : BikeType.GRAVEL;
			}
		};
	}

	/** Whether this slot has a component to draw. */
	public static boolean present(GeoRenderState state, int slot) {
		int parts = state.getOrDefaultGeckolibData(BikeDataTickets.COMPONENTS, BikeComponents.ALL_PARTS_MASK);
		return (parts & BikeComponents.bit(slot)) != 0;
	}

	@Override public boolean shouldRenderBone(EntityRenderState state) {
		return present(state, slot);
	}

	@Override protected void renderBone(RenderPassInfo<EntityRenderState> info, GeoBone bone, SubmitNodeCollector collector) {
		BikeType donor = donor(info.renderState(), slot);
		GeoBone part = GeckoLibResources.getBakedModels().getModel(BikeGeoModel.component(donor, boneName))
			.getBone("component").orElse(null);
		var renderType = getRenderType(info.renderState(), BikeGeoModel.texture(donor));
		if (part == null || renderType == null) return;
		int light = info.packedLight(), overlay = info.packedOverlay(), color = info.renderColor();
		collector.submitCustomGeometry(info.poseStack(), renderType, (pose, vertices) -> {
			PoseStack stack = new PoseStack();
			stack.last().set(pose);
			// The pose is at the frame attachment; component geometry is authored around its own equivalent pivot.
			part.translateAwayFromPivotPoint(stack);
			BoneGeometry.draw(this, part, stack, vertices, light, overlay, color);
		});
	}
}
