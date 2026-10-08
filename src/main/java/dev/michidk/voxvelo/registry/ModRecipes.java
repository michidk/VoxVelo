package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.recipe.BikeAssemblyRecipe;
import dev.michidk.voxvelo.recipe.WheelRetreadRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Recipe serializers. Recoloring bikes and frames uses vanilla {@code crafting_dye} recipes, so it needs none. */
public final class ModRecipes {
	private ModRecipes() {}

	public static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxVelo.id("bike_assembly"), BikeAssemblyRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxVelo.id("wheel_retread"), WheelRetreadRecipe.SERIALIZER);
	}
}
