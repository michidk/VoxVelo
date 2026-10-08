package dev.michidk.voxvelo.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.bike.BikeComponents;
import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.bike.BikeType;
import net.minecraft.resources.Identifier;

/**
 * Picks the GeckoLib model and texture for the bike type being rendered. All three bikes share one
 * animation file; the bones they animate (crank, pedals) have the same names in every model.
 */
public class BikeGeoModel extends GeoModel<BikeEntity> {
	private static final Identifier ANIMATIONS = VoxVelo.id("entity/bicycle");

	private static BikeType typeOf(GeoRenderState state) {
		return state.getOrDefaultGeckolibData(BikeDataTickets.BIKE_TYPE, BikeType.GRAVEL);
	}

	private static String fileName(BikeType type) {
		return type.id + "_bike";
	}

	/** The bone that holds the part in a {@link BikeComponents} slot, the same in every frame model. */
	public static String bone(int slot) {
		return switch (slot) {
			case BikeComponents.FRONT -> "front_wheel";
			case BikeComponents.REAR -> "rear_wheel";
			case BikeComponents.BARS -> "handlebar";
			case BikeComponents.SADDLE -> "saddle";
			case BikeComponents.PEDALS -> "crank";
			default -> throw new IllegalArgumentException("Unknown bike slot " + slot);
		};
	}

	public static Identifier model(BikeType type) {
		return VoxVelo.id("entity/bike_frames/" + fileName(type));
	}

	public static Identifier component(BikeType type, String boneName) {
		return VoxVelo.id("entity/bike_parts/" + fileName(type) + "_" + boneName);
	}

	public static Identifier texture(BikeType type) {
		return VoxVelo.id("textures/entity/bikes/" + fileName(type) + ".png");
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
