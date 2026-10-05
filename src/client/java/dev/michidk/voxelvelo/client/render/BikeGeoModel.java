package dev.michidk.voxelvelo.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.BikeType;
import net.minecraft.resources.Identifier;

/**
 * Picks the GeckoLib model and texture for the bike type being rendered. All three bikes share one
 * animation file; the bones they animate (crank, pedals) have the same names in every model.
 */
public class BikeGeoModel extends GeoModel<BikeEntity> {
	private static final Identifier ANIMATIONS = VoxelVelo.id("entity/bicycle");

	private static BikeType typeOf(GeoRenderState state) {
		return state.getOrDefaultGeckolibData(BikeDataTickets.BIKE_TYPE, BikeType.GRAVEL);
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return VoxelVelo.id("entity/" + typeOf(renderState).id + "_bike");
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return VoxelVelo.id("textures/entity/bicycle_" + typeOf(renderState).id + ".png");
	}

	@Override
	public Identifier getAnimationResource(BikeEntity animatable) {
		return ANIMATIONS;
	}
}
