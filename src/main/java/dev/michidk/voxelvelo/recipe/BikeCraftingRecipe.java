package dev.michidk.voxelvelo.recipe;

import com.mojang.serialization.MapCodec;
import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeComponents;
import dev.michidk.voxelvelo.item.BikeItem;
import dev.michidk.voxelvelo.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Two-ingredient recoloring and tire replacement; copy the original stack to retain its state. */
public final class BikeCraftingRecipe extends CustomRecipe {
    public static final RecipeSerializer<BikeCraftingRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(BikeCraftingRecipe::new), StreamCodec.unit(new BikeCraftingRecipe()));

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxelVelo.id("bike_service"), SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, VoxelVelo.id("bike_assembly"), BikeAssemblyRecipe.SERIALIZER);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack target = ItemStack.EMPTY, ingredient = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof BikeItem || ModItems.isFrame(stack.getItem()) || stack.is(ModItems.BIKE_WHEEL)) {
                if (!target.isEmpty()) return ItemStack.EMPTY;
                target = stack;
            } else {
                if (!ingredient.isEmpty()) return ItemStack.EMPTY;
                ingredient = stack;
            }
        }
        if (target.isEmpty() || ingredient.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = target.copyWithCount(1);
        if (target.is(ModItems.BIKE_WHEEL)) {
            if (!ingredient.is(ModItems.TIRE) || BikeComponents.tire(target) >= 1) return ItemStack.EMPTY;
            BikeComponents.tire(result, 1);
        } else {
            var dye = ingredient.get(DataComponents.DYE);
            if (dye == null) return ItemStack.EMPTY;
            BikeComponents.color(result, dye.getTextureDiffuseColor());
        }
        return result;
    }

    @Override
    public RecipeSerializer<BikeCraftingRecipe> getSerializer() { return SERIALIZER; }
}
