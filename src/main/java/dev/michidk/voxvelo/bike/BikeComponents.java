package dev.michidk.voxvelo.bike;

import dev.michidk.voxvelo.registry.ModComponents;
import dev.michidk.voxvelo.registry.ModEnchantments;
import dev.michidk.voxvelo.registry.ModItems;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Shared item/entity state contract. Wheels retain their metal component at zero tire condition.
 *
 * <p>Parts are interchangeable between frames: any wheel and either handlebar fits any frame. The frame keeps its own
 * mass, top speed, drivetrain, geometry and suspension; the tires and the handlebars bring their own handling, see
 * {@link #params}.
 */
public final class BikeComponents {
	public static final int FRONT = 0, REAR = 1, BARS = 2, SADDLE = 3, PEDALS = 4, COUNT = 5;
	/** Bit mask with one bit per installed slot, see {@link #bit}. */
	public static final int ALL_PARTS_MASK = (1 << COUNT) - 1;
	/** The parts a bike cannot be ridden without: both wheels, handlebars and saddle. Pedals only add drive. */
	public static final int RIDEABLE_MASK = bit(FRONT) | bit(REAR) | bit(BARS) | bit(SADDLE);
	public static final int DEFAULT_COLOR = 0xFFFFFF;

	/** Extra frontal area of the upright position on flat bars compared with drops, CdA in m^2. */
	private static final double FLAT_BAR_DRAG = 0.06;
	/** Extra steering lock flat bars give (wider bars, more leverage), degrees at low and at high speed. */
	private static final double FLAT_BAR_STEER_LOW = 5.0, FLAT_BAR_STEER_HIGH = 1.5;
	private static final String VEHICLE_DAMAGE = "voxvelo_vehicle_damage";
	/** Custom data key of a wheel's tire condition, 0 (burst) to 1 (new). */
	private static final String TIRE_CONDITION = "voxvelo_tire";

	private BikeComponents() {}

	/** The mask bit of a slot. */
	public static int bit(int slot) {
		return 1 << slot;
	}

	/** Packs the condition of both tires, in whole percent, into one synced int: front in the low byte. */
	public static int packTires(int frontPercent, int rearPercent) {
		return (frontPercent & 0xFF) | (rearPercent & 0xFF) << 8;
	}

	public static int tirePercent(int packed, int slot) {
		return (packed >> (slot * 8)) & 0xFF;
	}

	/** The part a new bike of this type comes with. */
	public static Item expected(BikeType type, int slot) {
		return switch (slot) {
			case FRONT, REAR -> ModItems.BIKE_WHEEL;
			case BARS -> type == BikeType.MOUNTAIN ? ModItems.FLAT_HANDLEBARS : ModItems.DROP_HANDLEBARS;
			case SADDLE -> ModItems.BIKE_SADDLE;
			case PEDALS -> ModItems.PEDAL_SET;
			default -> throw new IllegalArgumentException("Unknown bike slot " + slot);
		};
	}

	/** A complete stock part for a new bike of this type, wheels with the type's own tires. */
	public static ItemStack stock(BikeType type, int slot) {
		return slot == FRONT || slot == REAR ? ModItems.wheel(TireTread.nativeFor(type)) : new ItemStack(expected(type, slot));
	}

	/** Whether the slot takes this part, on any frame. */
	public static boolean fits(int slot, ItemStack stack) {
		return switch (slot) {
			case FRONT, REAR -> stack.is(ModItems.BIKE_WHEEL);
			case BARS -> stack.is(ModItems.FLAT_HANDLEBARS) || stack.is(ModItems.DROP_HANDLEBARS);
			case SADDLE -> stack.is(ModItems.BIKE_SADDLE);
			case PEDALS -> stack.is(ModItems.PEDAL_SET);
			default -> false;
		};
	}

	public static boolean accepts(int slot, ItemStack stack) {
		return stack.isEmpty() || (fits(slot, stack) && stack.getCount() == 1);
	}

	public static int color(ItemStack stack) {
		return stack.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(DEFAULT_COLOR)).rgb();
	}

	public static void color(ItemStack stack, int rgb) {
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF));
	}

	public static float vehicleDamage(ItemStack stack) {
		float value = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
			.getFloatOr(VEHICLE_DAMAGE, 0.0F);
		return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
	}

	public static void vehicleDamage(ItemStack stack, float value) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack,
			tag -> tag.putFloat(VEHICLE_DAMAGE, Math.max(0.0F, value)));
	}

	public static double tire(ItemStack wheel) {
		if (wheel.isEmpty()) return 0;
		double value = wheel.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
			.getDoubleOr(TIRE_CONDITION, 1.0);
		return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 1;
	}

	public static void tire(ItemStack wheel, double value) {
		CustomData.update(DataComponents.CUSTOM_DATA, wheel,
			tag -> tag.putDouble(TIRE_CONDITION, Math.clamp(value, 0, 1)));
	}

	/** The tire on a wheel. Wheels from before treads existed were made with the plain (now gravel) tire. */
	public static TireTread tread(ItemStack wheel) {
		return wheel.getOrDefault(ModComponents.TREAD, TireTread.GRAVEL);
	}

	public static void tread(ItemStack wheel, TireTread tread) {
		wheel.set(ModComponents.TREAD, tread);
	}

	/** Gives a wheel from before treads existed the tire it is taken to have, so it keeps it wherever it goes next. */
	public static void stampTread(ItemStack wheel, TireTread legacy) {
		if (wheel.is(ModItems.BIKE_WHEEL) && !wheel.has(ModComponents.TREAD)) tread(wheel, legacy);
	}

	public static boolean flatBars(ItemStack bars) {
		return bars.is(ModItems.FLAT_HANDLEBARS);
	}

	// ---------------------------------------------------------------- bike items

	/** The item a placed bike is picked up as: its type, frame color, installed parts, condition and Speed Boost. */
	public static ItemStack bikeItem(BikeType type, int color, ItemContainerContents parts, float vehicleDamage, int speedBoost,
		HolderLookup.Provider registries) {
		ItemStack stack = new ItemStack(ModItems.forType(type));
		color(stack, color);
		stack.set(DataComponents.CONTAINER, parts);
		vehicleDamage(stack, vehicleDamage);
		if (speedBoost > 0) {
			stack.enchant(speedBoost(registries), speedBoost);
		}
		return stack;
	}

	/** The Speed Boost level of a bike item, within what a bike supports. */
	public static int speedBoostLevel(ItemStack stack, HolderLookup.Provider registries) {
		return BikeSpeedBoost.clampLevel(stack.getEnchantments().getLevel(speedBoost(registries)));
	}

	private static Holder<Enchantment> speedBoost(HolderLookup.Provider registries) {
		return registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SPEED_BOOST);
	}

	// ---------------------------------------------------------------- setup: the installed parts that change handling

	/** Packs the handling-relevant parts into one synced int: front tread, rear tread, flat bars. */
	public static int setup(BikeType type, List<ItemStack> parts) {
		ItemStack front = parts.get(FRONT), rear = parts.get(REAR), bars = parts.get(BARS);
		TireTread stockTread = TireTread.nativeFor(type);
		int f = (front.isEmpty() ? stockTread : tread(front)).ordinal();
		int r = (rear.isEmpty() ? stockTread : tread(rear)).ordinal();
		boolean flat = bars.isEmpty() ? type == BikeType.MOUNTAIN : flatBars(bars);
		return f | r << 2 | (flat ? 1 << 4 : 0);
	}

	public static int stockSetup(BikeType type) {
		int tread = TireTread.nativeFor(type).ordinal();
		return tread | tread << 2 | (type == BikeType.MOUNTAIN ? 1 << 4 : 0);
	}

	public static TireTread frontTread(int setup) { return TireTread.byOrdinal(setup & 3); }
	public static TireTread rearTread(int setup) { return TireTread.byOrdinal(setup >> 2 & 3); }
	public static boolean flatBars(int setup) { return (setup & 1 << 4) != 0; }

	/**
	 * The frame's tuning with the installed tires and handlebars. The tire parameters are the average of the two
	 * tires' own types; the handlebars shift drag and steering lock relative to the bars the frame comes with.
	 * A stock bike gets exactly its type's parameters.
	 */
	public static BikePhysics.Params params(BikeType type, int setup) {
		BikePhysics.Params base = type.params();
		BikePhysics.Params front = frontTread(setup).origin.params(), rear = rearTread(setup).origin.params();
		double bars = (flatBars(setup) ? 1 : 0) - (type == BikeType.MOUNTAIN ? 1 : 0);
		return base
			.withTires((front.pavedRolling() + rear.pavedRolling()) / 2, (front.looseRolling() + rear.looseRolling()) / 2,
				(front.looseGrip() + rear.looseGrip()) / 2)
			.withCockpit(base.dragArea() + bars * FLAT_BAR_DRAG, base.steerAngleLowSpeed() + bars * FLAT_BAR_STEER_LOW,
				base.steerAngleHighSpeed() + bars * FLAT_BAR_STEER_HIGH);
	}

	/** The frame's rig with the grips where the installed handlebars put them. */
	public static BikeRig rig(BikeType type, int setup) {
		return type.rig.withBarsOf(flatBars(setup) ? BikeType.MOUNTAIN.rig : BikeType.GRAVEL.rig);
	}
}
