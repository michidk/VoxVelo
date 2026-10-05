package dev.michidk.voxelvelo.item;

import dev.michidk.voxelvelo.bike.BikeComponents;
import dev.michidk.voxelvelo.bike.TireTread;
import dev.michidk.voxelvelo.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;

public class BikePartItem extends Item {
    private final boolean wheel;
    public BikePartItem(Properties properties, boolean wheel) {
        super(properties);
        this.wheel = wheel;
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        if (stack.is(ModItems.BIKE_WRENCH))
            lines.accept(Component.translatable("tooltip.voxelvelo.wrench_use"));
        TireTread tire = ModItems.treadOf(stack);
        if (tire != null) lines.accept(Component.translatable("tooltip.voxelvelo.tire." + tire.id));
        if (stack.is(ModItems.DROP_HANDLEBARS)) lines.accept(Component.translatable("tooltip.voxelvelo.bars_drop"));
        if (stack.is(ModItems.FLAT_HANDLEBARS)) lines.accept(Component.translatable("tooltip.voxelvelo.bars_flat"));
        if (wheel) {
            lines.accept(Component.translatable("tooltip.voxelvelo.tread",
                    Component.translatable("menu.voxelvelo.tread." + BikeComponents.tread(stack).id)));
            int percent = (int) Math.ceil(BikeComponents.tire(stack) * 100);
            lines.accept(Component.translatable("tooltip.voxelvelo.tire_condition", percent));
            if (percent == 0) lines.accept(Component.translatable("tooltip.voxelvelo.replace_tire"));
        }
    }
}
