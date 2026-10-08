package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.bike.BikeType;
import dev.michidk.voxvelo.bike.TireTread;
import dev.michidk.voxvelo.item.BikeHelmetItem;
import dev.michidk.voxvelo.item.BikeItem;
import dev.michidk.voxvelo.item.BikePartItem;
import dev.michidk.voxvelo.item.BikeWrenchItem;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ModItems {
	private static final Map<BikeType, Item> BIKES = new EnumMap<>(BikeType.class);

	public static final Item GRAVEL_BIKE = registerBike(BikeType.GRAVEL, "gravel_bike");
	public static final Item MOUNTAIN_BIKE = registerBike(BikeType.MOUNTAIN, "mountain_bike");
	public static final Item ROAD_BIKE = registerBike(BikeType.ROAD, "road_bike");

	/** The gravel tire; it keeps the plain "tire" ID it had before there were several treads. */
	public static final Item TIRE = registerPart("tire", false);
	public static final Item ROAD_TIRE = registerPart("road_tire", false);
	public static final Item MOUNTAIN_TIRE = registerPart("mountain_tire", false);
	public static final Item BARE_WHEEL = registerPart("bare_wheel", false);
	public static final Item BIKE_WHEEL = registerPart("bike_wheel", true);
	public static final Item MOUNTAIN_BIKE_FRAME = registerPart("mountain_bike_frame", false);
	public static final Item GRAVEL_BIKE_FRAME = registerPart("gravel_bike_frame", false);
	public static final Item ROAD_BIKE_FRAME = registerPart("road_bike_frame", false);
	public static final Item FLAT_HANDLEBARS = registerPart("flat_handlebars", false);
	public static final Item DROP_HANDLEBARS = registerPart("drop_handlebars", false);
	public static final Item BIKE_SADDLE = registerPart("bike_saddle", false);
	public static final Item PEDAL_SET = registerPart("pedal_set", false);
	public static final Item BIKE_WRENCH = register("bike_wrench", BikeWrenchItem::new, new Item.Properties());
	public static final Item BIKE_HELMET = register("bike_helmet", BikeHelmetItem::new, new Item.Properties());

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VoxVelo.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBike(BikeType type, String name) {
		Item item = register(name, properties -> new BikeItem(type, properties), new Item.Properties().stacksTo(1).enchantable(15));
		BIKES.put(type, item);
		return item;
	}

	private static Item registerPart(String name, boolean wheel) {
		return register(name, properties -> new BikePartItem(properties, wheel), new Item.Properties());
	}

	public static Item frameFor(BikeType type) {
		return switch (type) {
			case MOUNTAIN -> MOUNTAIN_BIKE_FRAME;
			case GRAVEL -> GRAVEL_BIKE_FRAME;
			case ROAD -> ROAD_BIKE_FRAME;
		};
	}

	public static Item tireFor(TireTread tread) {
		return switch (tread) {
			case ROAD -> ROAD_TIRE;
			case GRAVEL -> TIRE;
			case MOUNTAIN -> MOUNTAIN_TIRE;
		};
	}

	/** The tread of a tire item, or null if the stack is not a tire. */
	public static @Nullable TireTread treadOf(ItemStack stack) {
		for (TireTread tread : TireTread.values()) if (stack.is(tireFor(tread))) return tread;
		return null;
	}

	public static ItemStack wheel(TireTread tread) {
		ItemStack wheel = new ItemStack(BIKE_WHEEL);
		wheel.set(ModComponents.TREAD, tread);
		return wheel;
	}

	public static Item forType(BikeType type) {
		return BIKES.get(type);
	}

	public static void register() {
		// Read old inventory stacks using the canonical road-bike IDs.
		((FabricRegistry) BuiltInRegistries.ITEM).addAlias(VoxVelo.id("race_bike"), VoxVelo.id("road_bike"));
		((FabricRegistry) BuiltInRegistries.ITEM).addAlias(VoxVelo.id("race_bike_frame"), VoxVelo.id("road_bike_frame"));

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, VoxVelo.id("bikes"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.voxvelo.bikes"))
				.icon(MOUNTAIN_BIKE::getDefaultInstance)
				.displayItems((parameters, output) -> {
					output.accept(MOUNTAIN_BIKE);
					output.accept(GRAVEL_BIKE);
					output.accept(ROAD_BIKE);
					output.accept(ROAD_TIRE);
					output.accept(TIRE);
					output.accept(MOUNTAIN_TIRE);
					output.accept(BARE_WHEEL);
					for (TireTread tread : TireTread.values()) output.accept(wheel(tread));
					output.accept(MOUNTAIN_BIKE_FRAME);
					output.accept(GRAVEL_BIKE_FRAME);
					output.accept(ROAD_BIKE_FRAME);
					output.accept(FLAT_HANDLEBARS);
					output.accept(DROP_HANDLEBARS);
					output.accept(BIKE_SADDLE);
					output.accept(PEDAL_SET);
					output.accept(BIKE_WRENCH);
					output.accept(BIKE_HELMET);
				}).build());
	}
}
