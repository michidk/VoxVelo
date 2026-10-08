package dev.michidk.voxvelo.bikes.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Repairs a parked bike (attack it) and takes one apart into its frame and parts (use it on the bike). Both wear the
 * wrench; the bike does the work, see BikeEntity and BikeWear.
 */
public class BikeWrenchItem extends Item {
	private static final int DURABILITY = 256;

	public BikeWrenchItem(Properties properties) {
		super(properties.durability(DURABILITY));
	}

	// Item tooltips are still added by overriding this hook, which Mojang marks deprecated.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		lines.accept(Component.translatable("tooltip.voxvelo_bikes.wrench_use"));
	}
}
