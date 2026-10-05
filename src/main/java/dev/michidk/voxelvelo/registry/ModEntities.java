package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> BICYCLE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, VoxelVelo.id("bicycle"));

	public static final EntityType<BikeEntity> BICYCLE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BICYCLE_KEY,
		EntityType.Builder.<BikeEntity>of(BikeEntity::new, MobCategory.MISC)
			.sized(0.9F, 1.0F)
			.eyeHeight(0.9F)
			.noLootTable()
			.clientTrackingRange(10)
			.updateInterval(1)
			.dontTrackDeltas()
			.build(BICYCLE_KEY)
	);

	private ModEntities() {
	}

	public static void register() {
		// Touching the class registers the entity type.
	}
}
