package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.recipe.BikeAssemblyRecipe;
import dev.michidk.voxelvelo.recipe.WheelRetreadRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Recipe serializers. Recoloring bikes and frames uses vanilla {@code crafting_dye} recipes, so it needs none. */
public final class ModRecipes {
    private ModRecipes() {}

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxelVelo.id("bike_assembly"), BikeAssemblyRecipe.SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxelVelo.id("wheel_retread"), WheelRetreadRecipe.SERIALIZER);
    }
}
