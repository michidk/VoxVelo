package dev.michidk.voxvelo.bikes.registry;

import dev.michidk.voxvelo.bikes.VoxVelo;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeHitboxPart;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> BICYCLE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, VoxVelo.id("bicycle"));
	public static final ResourceKey<EntityType<?>> BICYCLE_PART_KEY = ResourceKey.create(Registries.ENTITY_TYPE, VoxVelo.id("bicycle_part"));

	public static final EntityType<BikeEntity> BICYCLE = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BICYCLE_KEY,
		EntityType.Builder.<BikeEntity>of(BikeEntity::new, MobCategory.MISC)
			// Block collision still uses the parent; targeting uses the five rotating child boxes below.
			.sized(0.35F, 0.94F)
			.eyeHeight(0.95F)
			.noLootTable()
			.clientTrackingRange(10)
			.updateInterval(1)
			.dontTrackDeltas()
			.build(BICYCLE_KEY)
	);

	public static final EntityType<BikeHitboxPart> BICYCLE_PART = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BICYCLE_PART_KEY,
		EntityType.Builder.<BikeHitboxPart>of(BikeHitboxPart::new, MobCategory.MISC)
			.sized(0.48F, 0.94F)
			.noSummon()
			.noSave()
			.noLootTable()
			.clientTrackingRange(10)
			.updateInterval(1)
			.dontTrackDeltas()
			.build(BICYCLE_PART_KEY)
	);

	private ModEntities() {
	}

	public static void register() {
		// Touching the class registers the entity type.
	}
}
