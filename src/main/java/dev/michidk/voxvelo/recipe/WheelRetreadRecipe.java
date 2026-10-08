package dev.michidk.voxvelo.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.michidk.voxvelo.bike.BikeComponents;
import dev.michidk.voxvelo.bike.TireTread;
import dev.michidk.voxvelo.registry.ModItems;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

/**
 * Fits a fresh tire of one tread to a built wheel: repairs a worn tire or swaps the tread. A copy of the wheel keeps
 * its other state. Unlike a special recipe it has a recipe-book entry, one per tread.
 */
public final class WheelRetreadRecipe extends NormalCraftingRecipe {
	public static final MapCodec<WheelRetreadRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
			CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
			TireTread.CODEC.fieldOf("tread").forGetter(r -> r.tread)
	).apply(i, WheelRetreadRecipe::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, WheelRetreadRecipe> STREAM_CODEC = StreamCodec.composite(
			Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
			CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
			TireTread.STREAM_CODEC, r -> r.tread,
			WheelRetreadRecipe::new);
	public static final RecipeSerializer<WheelRetreadRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final TireTread tread;

	public WheelRetreadRecipe(Recipe.CommonInfo commonInfo, CraftingBookInfo bookInfo, TireTread tread) {
		super(commonInfo, bookInfo);
		this.tread = tread;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.ingredientCount() != 2) return false;
		ItemStack wheel = wheel(input);
		// A wheel already on a full tire of this tread has nothing to gain.
		return !wheel.isEmpty() && hasTire(input) && (BikeComponents.tire(wheel) < 1 || BikeComponents.tread(wheel) != tread);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack result = wheel(input).copyWithCount(1);
		if (result.isEmpty()) return ItemStack.EMPTY;
		BikeComponents.tire(result, 1);
		BikeComponents.tread(result, tread);
		return result;
	}

	private static ItemStack wheel(CraftingInput input) {
		for (ItemStack stack : input.items()) if (stack.is(ModItems.BIKE_WHEEL)) return stack;
		return ItemStack.EMPTY;
	}

	private boolean hasTire(CraftingInput input) {
		for (ItemStack stack : input.items()) if (stack.is(ModItems.tireFor(tread))) return true;
		return false;
	}

	@Override
	protected PlacementInfo createPlacementInfo() {
		return PlacementInfo.create(List.of(Ingredient.of(ModItems.BIKE_WHEEL), Ingredient.of(ModItems.tireFor(tread))));
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new ShapelessCraftingRecipeDisplay(
				List.of(new SlotDisplay.ItemSlotDisplay(ModItems.BIKE_WHEEL), new SlotDisplay.ItemSlotDisplay(ModItems.tireFor(tread))),
				new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(ModItems.wheel(tread))),
				new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
	}

	@Override
	public RecipeSerializer<WheelRetreadRecipe> getSerializer() { return SERIALIZER; }
}
