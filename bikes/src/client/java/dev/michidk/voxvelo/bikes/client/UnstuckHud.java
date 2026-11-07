package dev.michidk.voxvelo.bikes.client;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
/** Shows the server's wall recovery countdown to the rider. */
public final class UnstuckHud {
public static void register() {
net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
net.minecraft.resources.Identifier.fromNamespaceAndPath("voxvelo_bikes", "unstuck"), (graphics, delta) -> {
	var mc = Minecraft.getInstance();
	BikeEntity bike = BikeEntity.riddenBy(mc.player);
	if (bike == null || bike.unstuckSeconds() == 0) return;
	Component text = Component.translatable("voxvelo_bikes.unstuck.countdown", bike.unstuckSeconds());
	int width = mc.font.width(text), x = (graphics.guiWidth() - width) / 2, y = graphics.guiHeight() / 2 + 20;
	graphics.fill(x - 8, y - 5, x + width + 8, y + 14, 0xB0000000);
	graphics.text(mc.font, text, x, y, 0xFFFFD060);
});
}
}
