package dev.michidk.voxvelo.fitnesslib.client.fitness;
import net.minecraft.network.chat.Component;
/** Controls whether a panel needs an active vehicle. */
public enum HudVisibility {
ALWAYS, RIDING, NEVER;
public boolean visible(boolean riding) { return this == ALWAYS || this == RIDING && riding; }
public Component label() { return Component.translatable("voxvelo_fitness_lib.hud.visibility." + this.name().toLowerCase(java.util.Locale.ROOT)); }
}
