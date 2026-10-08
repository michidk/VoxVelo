package dev.michidk.voxvelo.recipe;

import dev.michidk.voxvelo.bike.BikeComponents;
import dev.michidk.voxvelo.item.BikeItem;
import dev.michidk.voxvelo.registry.ModItems;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * A normal shaped, recipe-book-visible assembly recipe that transfers consumed component data. The recipe JSON decides
 * which parts are accepted (any wheel, either handlebar); each consumed part fills the first free slot it fits.
 */
public record BikeAssemblyRecipe(ShapedRecipe recipe) implements CraftingRecipe {
	public static final RecipeSerializer<BikeAssemblyRecipe> SERIALIZER = new RecipeSerializer<>(
			ShapedRecipe.MAP_CODEC.xmap(BikeAssemblyRecipe::new, BikeAssemblyRecipe::recipe),
			ShapedRecipe.STREAM_CODEC.map(BikeAssemblyRecipe::new, BikeAssemblyRecipe::recipe));

	@Override public boolean matches(CraftingInput input, Level level) { return recipe.matches(input, level); }
	@Override public ItemStack assemble(CraftingInput input) {
		ItemStack result = recipe.assemble(input);
		if (!(result.getItem() instanceof BikeItem bike)) return ItemStack.EMPTY;
		NonNullList<ItemStack> parts = NonNullList.withSize(BikeComponents.COUNT, ItemStack.EMPTY);
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) continue;
			if (stack.is(ModItems.frameFor(bike.bikeType()))) BikeComponents.color(result, BikeComponents.color(stack));
			else for (int i = 0; i < parts.size(); i++) {
				if (parts.get(i).isEmpty() && BikeComponents.fits(i, stack)) {
					ItemStack part = stack.copyWithCount(1);
					BikeComponents.stampTread(part, BikeComponents.tread(part)); // What its tooltip showed.
					parts.set(i, part);
					break;
				}
			}
		}
		result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
		return result;
	}
	@Override public RecipeSerializer<BikeAssemblyRecipe> getSerializer() { return SERIALIZER; }
	@Override public boolean showNotification() { return recipe.showNotification(); }
	@Override public String group() { return recipe.group(); }
	@Override public CraftingBookCategory category() { return recipe.category(); }
	@Override public PlacementInfo placementInfo() { return recipe.placementInfo(); }
	@Override public List<RecipeDisplay> display() { return recipe.display(); }
}
