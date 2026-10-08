package dev.michidk.voxvelo.bikes.registry;

import dev.michidk.voxvelo.bikes.VoxVelo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Keys for VoxVelo's data-driven enchantments. */
public final class ModEnchantments {
	public static final ResourceKey<Enchantment> SPEED_BOOST = ResourceKey.create(
		Registries.ENCHANTMENT, VoxVelo.id("speed_boost"));

	private ModEnchantments() {
	}
}
