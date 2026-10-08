package dev.michidk.voxvelo.fitness.client.road;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The set of blocks that count as road, resolved from the player's list of ids ({@code minecraft:stone}) and
 * tags ({@code #minecraft:base_stone_overworld}). Unknown or malformed entries are skipped, never fatal.
 * Rebuilt only when the list changes, so block states are matched without string work.
 */
public final class RoadBlocks {
	private final List<Block> blocks = new ArrayList<>();
	private final List<TagKey<Block>> tags = new ArrayList<>();
	private List<String> source = List.of();

	/** Returns true if the entry names a block or tag that exists in this game. */
	public static boolean isKnown(String entry) {
		if (entry.startsWith("#")) {
			return Identifier.tryParse(entry.substring(1)) != null;
		}
		Identifier id = Identifier.tryParse(entry);
		return id != null && BuiltInRegistries.BLOCK.getOptional(id).isPresent();
	}

	/** Re-resolves the entries if the list differs from the last one seen. */
	public void update(List<String> entries) {
		if (entries.equals(this.source)) {
			return;
		}
		this.source = List.copyOf(entries);
		this.blocks.clear();
		this.tags.clear();
		for (String entry : entries) {
			if (entry.startsWith("#")) {
				Identifier id = Identifier.tryParse(entry.substring(1));
				if (id != null) {
					this.tags.add(TagKey.create(Registries.BLOCK, id));
				}
			} else {
				Identifier id = Identifier.tryParse(entry);
				if (id != null) {
					BuiltInRegistries.BLOCK.getOptional(id).ifPresent(this.blocks::add);
				}
			}
		}
	}

	public boolean matches(BlockState state) {
		for (Block block : this.blocks) {
			if (state.is(block)) {
				return true;
			}
		}
		for (TagKey<Block> tag : this.tags) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
