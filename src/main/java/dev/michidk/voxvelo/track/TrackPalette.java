package dev.michidk.voxvelo.track;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Match vanilla/modded slab names, with a stable smooth-stone fallback for materials without slabs. */
final class TrackPalette {
	static BlockState slabFor(BlockState material) {
		if (material.getBlock() instanceof SlabBlock) return material.getBlock().defaultBlockState();
		Identifier id = BuiltInRegistries.BLOCK.getKey(material.getBlock());
		String path = id.getPath();
		if (path.endsWith("_planks")) path = path.substring(0, path.length() - 7);
		else if (path.endsWith("_bricks")) path = path.substring(0, path.length() - 1);
		else if (path.equals("bricks")) path = "brick";
		else if (path.equals("quartz_block")) path = "quartz";
		else if (path.equals("purpur_block")) path = "purpur";
		Block slab = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), path + "_slab"))
			.orElse(Blocks.SMOOTH_STONE_SLAB);
		return (slab instanceof SlabBlock ? slab : Blocks.SMOOTH_STONE_SLAB).defaultBlockState();
	}
}
