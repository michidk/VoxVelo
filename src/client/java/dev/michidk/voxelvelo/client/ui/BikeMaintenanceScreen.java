package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.inventory.BikeMaintenanceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Five labeled component slots, condition, and two short instructions. */
public final class BikeMaintenanceScreen extends AbstractContainerScreen<BikeMaintenanceMenu> {
    public BikeMaintenanceScreen(BikeMaintenanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 228);
        inventoryLabelY = 134;
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        String[] labels = {"front", "rear", "bars", "seat", "pedals"};
        for (int i = 0; i < labels.length; i++) graphics.centeredText(font,
                Component.translatable("menu.voxelvelo." + labels[i]), 24 + 31 * i, 22, 0xFF404040);
        graphics.text(font, Component.translatable(menu.conditionKey()), 8, 62, 0xFF404040, false);
        graphics.text(font, Component.translatable("menu.voxelvelo.tires_short", menu.frontCondition(), menu.rearCondition()),
                8, 74, 0xFF404040, false);
        graphics.textWithWordWrap(font, Component.translatable("menu.voxelvelo.repair_short"), 8, 88, 160, 0xFF404040, false);
        graphics.textWithWordWrap(font, Component.translatable("menu.voxelvelo.disassemble_short"), 8, 108, 160, 0xFF404040, false);
    }
}
