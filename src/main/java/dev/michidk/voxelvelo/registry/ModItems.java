package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeType;
import dev.michidk.voxelvelo.bike.TireTread;
import dev.michidk.voxelvelo.item.BikeHelmetItem;
import dev.michidk.voxelvelo.item.BikeItem;
import dev.michidk.voxelvelo.item.BikePartItem;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ModItems {
	private static final Map<BikeType, Item> BIKES = new EnumMap<>(BikeType.class);

	public static final Item ROAD_BIKE = registerBike(BikeType.ROAD, "road_bike");
	public static final Item GRAVEL_BIKE = registerBike(BikeType.GRAVEL, "gravel_bike");
	public static final Item MOUNTAIN_BIKE = registerBike(BikeType.MOUNTAIN, "mountain_bike");

	/** The gravel tire; it keeps the plain "tire" ID it had before there were several treads. */
	public static final Item TIRE = registerPart("tire", false);
	public static final Item ROAD_TIRE = registerPart("road_tire", false);
	public static final Item MOUNTAIN_TIRE = registerPart("mountain_tire", false);
	public static final Item BARE_WHEEL = registerPart("bare_wheel", false);
	public static final Item BIKE_WHEEL = registerPart("bike_wheel", true);
	public static final Item MOUNTAIN_BIKE_FRAME = registerPart("mountain_bike_frame", false);
	public static final Item GRAVEL_BIKE_FRAME = registerPart("gravel_bike_frame", false);
	public static final Item RACE_BIKE_FRAME = registerPart("race_bike_frame", false);
	public static final Item FLAT_HANDLEBARS = registerPart("flat_handlebars", false);
	public static final Item DROP_HANDLEBARS = registerPart("drop_handlebars", false);
	public static final Item BIKE_SADDLE = registerPart("bike_saddle", false);
	public static final Item PEDAL_SET = registerPart("pedal_set", false);
	public static final Item BIKE_WRENCH = registerPart("bike_wrench", false);
	public static final Item BIKE_HELMET = registerHelmet("bike_helmet");

	public static final Item RACE_BIKE = registerBike(BikeType.ROAD, "race_bike");

	private ModItems() {
	}

	private static Item registerBike(BikeType type, String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VoxelVelo.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key,
			new BikeItem(type, new Item.Properties().stacksTo(1).enchantable(15).setId(key)));
		BIKES.put(type, item);
		return item;
	}

    private static Item registerHelmet(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VoxelVelo.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new BikeHelmetItem(new Item.Properties().setId(key)));
    }

    private static Item registerPart(String name, boolean wheel) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VoxelVelo.id(name));
        Item.Properties properties = new Item.Properties().setId(key);
        if (name.equals("bike_wrench")) properties.durability(256);
        return Registry.register(BuiltInRegistries.ITEM, key, new BikePartItem(properties, wheel));
    }

    public static Item frameFor(BikeType type) {
        return switch (type) {
            case MOUNTAIN -> MOUNTAIN_BIKE_FRAME;
            case GRAVEL -> GRAVEL_BIKE_FRAME;
            case ROAD -> RACE_BIKE_FRAME;
        };
    }

    public static boolean isFrame(Item item) {
        return item == MOUNTAIN_BIKE_FRAME || item == GRAVEL_BIKE_FRAME || item == RACE_BIKE_FRAME;
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
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, VoxelVelo.id("bikes"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.voxelvelo.bikes"))
				.icon(MOUNTAIN_BIKE::getDefaultInstance)
				.displayItems((parameters, output) -> {
			output.accept(MOUNTAIN_BIKE);
			output.accept(GRAVEL_BIKE);
			output.accept(RACE_BIKE);
			output.accept(ROAD_TIRE);
			output.accept(TIRE);
			output.accept(MOUNTAIN_TIRE);
			output.accept(BARE_WHEEL);
			for (TireTread tread : TireTread.values()) output.accept(wheel(tread));
			output.accept(MOUNTAIN_BIKE_FRAME);
			output.accept(GRAVEL_BIKE_FRAME);
			output.accept(RACE_BIKE_FRAME);
			output.accept(FLAT_HANDLEBARS);
			output.accept(DROP_HANDLEBARS);
			output.accept(BIKE_SADDLE);
			output.accept(PEDAL_SET);
			output.accept(BIKE_WRENCH);
			output.accept(BIKE_HELMET);
				}).build());
	}
}
