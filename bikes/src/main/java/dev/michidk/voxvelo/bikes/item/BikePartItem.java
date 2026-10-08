package dev.michidk.voxvelo.bikes.item;

import dev.michidk.voxvelo.bikes.bike.BikeComponents;
import dev.michidk.voxvelo.bikes.bike.TireTread;
import dev.michidk.voxvelo.bikes.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A bike part, tire or frame: plain items whose tooltips explain what they do on a bike. */
public class BikePartItem extends Item {
	private final boolean wheel;

	public BikePartItem(Properties properties, boolean wheel) {
		super(properties);
		this.wheel = wheel;
	}

	// Item tooltips are still added by overriding this hook, which Mojang marks deprecated.
	@SuppressWarnings("deprecation")
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, lines, flag);
		TireTread tire = ModItems.treadOf(stack);
		if (tire != null) lines.accept(Component.translatable("tooltip.voxvelo_bikes.tire." + tire.id));
		if (stack.is(ModItems.DROP_HANDLEBARS)) lines.accept(Component.translatable("tooltip.voxvelo_bikes.bars_drop"));
		if (stack.is(ModItems.FLAT_HANDLEBARS)) lines.accept(Component.translatable("tooltip.voxvelo_bikes.bars_flat"));
		if (this.wheel) {
			lines.accept(Component.translatable("tooltip.voxvelo_bikes.tread",
				Component.translatable("menu.voxvelo_bikes.tread." + BikeComponents.tread(stack).id)));
			int percent = (int) Math.ceil(BikeComponents.tire(stack) * 100);
			lines.accept(Component.translatable("tooltip.voxvelo_bikes.tire_condition", percent));
			if (percent == 0) lines.accept(Component.translatable("tooltip.voxvelo_bikes.replace_tire"));
		}
	}
}
