package dev.michidk.voxelvelo.bike;

import dev.michidk.voxelvelo.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

/** Shared item/entity state contract. Wheels retain their metal component at zero tire condition. */
public final class BikeComponents {
    public static final int FRONT = 0, REAR = 1, BARS = 2, SADDLE = 3, PEDALS = 4, COUNT = 5;
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    private BikeComponents() {}

    public static Item expected(BikeType type, int slot) {
        return switch (slot) {
            case FRONT, REAR -> ModItems.BIKE_WHEEL;
            case BARS -> type == BikeType.MOUNTAIN ? ModItems.FLAT_HANDLEBARS : ModItems.DROP_HANDLEBARS;
            case SADDLE -> ModItems.BIKE_SADDLE;
            case PEDALS -> ModItems.PEDAL_SET;
            default -> throw new IllegalArgumentException("Unknown bike slot " + slot);
        };
    }

    public static boolean accepts(BikeType type, int slot, ItemStack stack) {
        return stack.isEmpty() || (stack.is(expected(type, slot)) && stack.getCount() == 1);
    }

    public static int color(ItemStack stack) {
        return stack.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(DEFAULT_COLOR)).rgb();
    }

    public static void color(ItemStack stack, int rgb) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF));
    }

    public static double tire(ItemStack wheel) {
        if (wheel.isEmpty()) return 0;
        double value = wheel.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getDoubleOr("voxelvelo_tire", 1.0);
        return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 1;
    }

    public static void tire(ItemStack wheel, double value) {
        CustomData.update(DataComponents.CUSTOM_DATA, wheel,
                tag -> tag.putDouble("voxelvelo_tire", Math.clamp(value, 0, 1)));
    }
}
