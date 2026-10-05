package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.bike.BikeType;
import dev.michidk.voxelvelo.item.BikeItem;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	private static final Map<BikeType, Item> BIKES = new EnumMap<>(BikeType.class);

	public static final Item ROAD_BIKE = registerBike(BikeType.ROAD, "road_bike");
	public static final Item GRAVEL_BIKE = registerBike(BikeType.GRAVEL, "gravel_bike");
	public static final Item MOUNTAIN_BIKE = registerBike(BikeType.MOUNTAIN, "mountain_bike");

	private ModItems() {
	}

	private static Item registerBike(BikeType type, String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VoxelVelo.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new BikeItem(type, new Item.Properties().stacksTo(1).setId(key)));
		BIKES.put(type, item);
		return item;
	}

	public static Item forType(BikeType type) {
		return BIKES.get(type);
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(ROAD_BIKE);
			output.accept(GRAVEL_BIKE);
			output.accept(MOUNTAIN_BIKE);
		});
	}
}
