package dev.michidk.voxelvelo.registry;

import dev.michidk.voxelvelo.VoxelVelo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Keys for VoxelVelo's data-driven enchantments. */
public final class ModEnchantments {
	public static final ResourceKey<Enchantment> SPEED_BOOST = ResourceKey.create(
		Registries.ENCHANTMENT, VoxelVelo.id("speed_boost"));

	private ModEnchantments() {
	}
}
