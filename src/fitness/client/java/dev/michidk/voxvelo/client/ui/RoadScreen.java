package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxvelo.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.client.fitness.FitnessContext;
import dev.michidk.voxvelo.client.road.RoadBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Settings for road following: whether it is on, which blocks count as road (a list the player edits) and how
 * wide a road may be. A block can be typed in, taken from under the player, or from the block being looked at.
 */
public class RoadScreen extends SettingsScreen {
	private static final int WIDTH = SettingsList.WIDTH;
	private static final int HALF = SettingsList.HALF;
	private static final int RIGHT = WIDTH - HALF;

	private final FitnessContext ctx = FitnessContext.get();
	private String inputText = "";
	/** Why the last block could not be added, shown under the input until the next attempt or edit. */
	private @Nullable Component error;

	public RoadScreen(Screen parent) {
		super(parent, Component.translatable("voxvelo.road.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addText(() -> Component.translatable("voxvelo.road.hint"), HINT);

		this.rows.addHeader(Component.translatable("voxvelo.road.section.following"));
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.roadFollowEnabled)
				.withTooltip(value -> Tips.of("voxvelo.road.follow"))
				.create(0, 0, HALF, 20, Component.translatable("voxvelo.road.follow"), (button, value) -> this.ctx.config.roadFollowEnabled = value),
			new ValueSlider(RIGHT, 0, HALF, 2.0, 16.0, 1.0, this.ctx.config.roadMaxHalfWidth,
				v -> Component.translatable("voxvelo.road.half_width", Math.round(v)), v -> this.ctx.config.roadMaxHalfWidth = (int) Math.round(v)).tip("voxvelo.road.half_width"));
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.junctionPrompt)
				.withTooltip(value -> Tips.of("voxvelo.road.junction"))
				.create(0, 0, HALF, 20, Component.translatable("voxvelo.road.junction"), (button, value) -> this.ctx.config.junctionPrompt = value),
			new ValueSlider(RIGHT, 0, HALF, 5.0, 120.0, 5.0, this.ctx.config.junctionCooldownSeconds,
				v -> Component.translatable("voxvelo.road.cooldown", Math.round(v)), v -> this.ctx.config.junctionCooldownSeconds = (int) Math.round(v)).tip("voxvelo.road.cooldown"));

		this.rows.addHeader(Component.translatable("voxvelo.road.section.terrain"));
		this.rows.addRow(new ValueSlider(0, 0, WIDTH, 10, 40, 1, this.ctx.config.slopeWindowBlocks,
			v -> Component.translatable("voxvelo.config.slope_window", Math.round(v)),
			v -> this.ctx.config.slopeWindowBlocks = (int) Math.round(v)).tip("voxvelo.config.slope_window"));

		this.rows.addHeader(Component.translatable("voxvelo.road.section.blocks"));
		EditBox input = new EditBox(this.font, 0, 0, HALF, 20, Component.translatable("voxvelo.road.input"));
		input.setTooltip(Tips.of("voxvelo.road.input"));
		input.setHint(Component.translatable("voxvelo.road.input_hint"));
		input.setMaxLength(80);
		input.setValue(this.inputText);
		input.setResponder(text -> {
			this.inputText = text;
			this.error = null;
		});
		this.rows.addRow(input, Button.builder(Component.translatable("voxvelo.road.add"), button -> {
			if (this.add(this.inputText)) {
				this.inputText = "";
				this.rebuildWidgets();
			}
		}).tooltip(Tips.of("voxvelo.road.add")).bounds(RIGHT, 0, HALF, 20).build());
		this.rows.addText(() -> this.error, ERROR);

		boolean inWorld = this.minecraft.level != null && this.minecraft.player != null;
		Button under = Button.builder(Component.translatable("voxvelo.road.add_under"), button -> this.addPicked(this.blockUnderPlayer(), "voxvelo.road.nothing_under"))
			.tooltip(Tips.of("voxvelo.road.add_under"))
			.bounds(0, 0, HALF, 20).build();
		Button looking = Button.builder(Component.translatable("voxvelo.road.add_looking"), button -> this.addPicked(this.blockLookedAt(), "voxvelo.road.not_looking"))
			.tooltip(Tips.of("voxvelo.road.add_looking"))
			.bounds(RIGHT, 0, HALF, 20).build();
		under.active = inWorld;
		looking.active = inWorld;
		this.rows.addRow(under, looking);

		List<String> blocks = this.ctx.config.roadBlocks;
		for (int i = 0; i < blocks.size(); i += 2) {
			if (i == blocks.size() - 1) {
				this.rows.addRow(this.entry(0, blocks.get(i)));
			} else {
				this.rows.addRow(this.entry(0, blocks.get(i)), this.entry(RIGHT, blocks.get(i + 1)));
			}
		}
		this.rows.addRow(Button.builder(Component.translatable("voxvelo.road.reset"), button -> {
			this.ctx.config.roadBlocks = new ArrayList<>(FitnessConfig.DEFAULT_ROAD_BLOCKS);
			this.error = null;
			this.rebuildWidgets();
		}).tooltip(Tips.of("voxvelo.road.reset")).bounds(0, 0, WIDTH, 20).build());
	}

	/** A listed road block; pressing it takes the block off the list. */
	private Button entry(int x, String entry) {
		return Button.builder(Component.literal(shorten(entry)), button -> this.remove(entry))
			.bounds(x, 0, HALF, 20)
			.tooltip(Tooltip.create(Component.translatable("voxvelo.road.remove", entry)))
			.build();
	}

	private static String shorten(String entry) {
		return entry.startsWith("minecraft:") ? entry.substring("minecraft:".length()) : entry.startsWith("#minecraft:") ? "#" + entry.substring("#minecraft:".length()) : entry;
	}

	private void remove(String entry) {
		List<String> list = new ArrayList<>(this.ctx.config.roadBlocks);
		list.remove(entry);
		this.ctx.config.roadBlocks = list;
		this.rebuildWidgets();
	}

	/** Adds a block taken from the world, or says why there was none to take. */
	private void addPicked(@Nullable String id, String missingKey) {
		if (id == null) {
			this.error = Component.translatable(missingKey);
		} else if (this.add(id)) {
			this.rebuildWidgets();
		}
	}

	/**
	 * Adds a block id or #tag, completing a missing namespace, if the game knows it and it is not listed yet. Returns
	 * whether it was added; if not, {@link #error} says why.
	 */
	private boolean add(String raw) {
		String entry = normalize(raw);
		if (entry == null || !RoadBlocks.isKnown(entry)) {
			this.error = Component.translatable("voxvelo.road.unknown");
			return false;
		}
		if (this.ctx.config.roadBlocks.contains(entry)) {
			this.error = Component.translatable("voxvelo.road.already");
			return false;
		}
		List<String> list = new ArrayList<>(this.ctx.config.roadBlocks);
		list.add(entry);
		this.ctx.config.roadBlocks = FitnessConfig.sanitizeBlocks(list);
		this.error = null;
		return true;
	}

	private static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty() || text.equals("#")) {
			return null;
		}
		if (text.startsWith("#")) {
			return text.contains(":") ? text : "#minecraft:" + text.substring(1);
		}
		return text.contains(":") ? text : "minecraft:" + text;
	}

	/** The block the player stands on, or null when there is only air below. */
	private @Nullable String blockUnderPlayer() {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return null;
		}
		BlockPos pos = BlockPos.containing(this.minecraft.player.getX(), this.minecraft.player.getY() - 0.2, this.minecraft.player.getZ());
		BlockState state = this.minecraft.level.getBlockState(pos);
		return state.isAir() ? null : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private @Nullable String blockLookedAt() {
		if (this.minecraft.level != null && this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return BuiltInRegistries.BLOCK.getKey(this.minecraft.level.getBlockState(hit.getBlockPos()).getBlock()).toString();
		}
		return null;
	}

	@Override
	protected void save() {
		this.ctx.config.save();
	}
}
