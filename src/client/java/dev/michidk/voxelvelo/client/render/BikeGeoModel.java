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

	private static String fileName(BikeType type) {
		return (type == BikeType.ROAD ? "race" : type.id) + "_bike";
	}

	public static Identifier model(BikeType type) {
		return VoxelVelo.id("entity/bikes/" + fileName(type));
	}

	public static Identifier texture(BikeType type) {
		return VoxelVelo.id("textures/entity/bikes/" + fileName(type) + ".png");
	}

	@Override
	public Identifier getModelResource(GeoRenderState renderState) {
		return model(typeOf(renderState));
	}

	@Override
	public Identifier getTextureResource(GeoRenderState renderState) {
		return texture(typeOf(renderState));
	}

	@Override
	public Identifier getAnimationResource(BikeEntity animatable) {
		return ANIMATIONS;
	}
}
