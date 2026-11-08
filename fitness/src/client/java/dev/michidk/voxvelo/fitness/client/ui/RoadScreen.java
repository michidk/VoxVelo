package dev.michidk.voxvelo.fitness.client.ui;

import dev.michidk.voxvelo.bikes.client.ui.SettingsList;
import dev.michidk.voxvelo.bikes.client.ui.SettingsScreen;
import dev.michidk.voxvelo.bikes.client.ui.Tips;
import dev.michidk.voxvelo.bikes.client.ui.ValueSlider;
import dev.michidk.voxvelo.fitness.client.fitness.FitnessContext;
import dev.michidk.voxvelo.fitness.client.road.RoadBlocks;
import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
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
		super(parent, Component.translatable("voxvelo_fitness_lib.road.title"));
	}

	@Override
	protected void addOptions() {
		this.rows.addHeader(Component.translatable("voxvelo_fitness_lib.road.section.following"));
		ValueSlider halfWidth = new ValueSlider(RIGHT, 0, HALF, 2.0, 16.0, 1.0, this.ctx.config.roadMaxHalfWidth,
			v -> Component.translatable("voxvelo_fitness_lib.road.half_width", Math.round(v)), v -> this.ctx.config.roadMaxHalfWidth = (int) Math.round(v)).tip("voxvelo_fitness_lib.road.half_width");
		ValueSlider cooldown = new ValueSlider(RIGHT, 0, HALF, 5.0, 120.0, 5.0, this.ctx.config.junctionCooldownSeconds,
			v -> Component.translatable("voxvelo_fitness_lib.road.cooldown", Math.round(v)), v -> this.ctx.config.junctionCooldownSeconds = (int) Math.round(v)).tip("voxvelo_fitness_lib.road.cooldown");
		CycleButton<Boolean> junction = CycleButton.onOffBuilder(this.ctx.config.junctionPrompt)
			.withTooltip(value -> Tips.of("voxvelo_fitness_lib.road.junction"))
			.create(0, 0, HALF, 20, Component.translatable("voxvelo_fitness_lib.road.junction"), (button, value) -> {
				this.ctx.config.junctionPrompt = value;
				cooldown.active = this.ctx.config.roadFollowEnabled && value;
			});
		CycleButton<Boolean> follow = CycleButton.onOffBuilder(this.ctx.config.roadFollowEnabled)
			.withTooltip(value -> Tips.of("voxvelo_fitness_lib.road.follow"))
			.create(0, 0, HALF, 20, Component.translatable("voxvelo_fitness_lib.road.follow"), (button, value) -> {
				this.ctx.config.roadFollowEnabled = value;
				this.updateDependents(halfWidth, junction, cooldown);
			});
		this.updateDependents(halfWidth, junction, cooldown);
		this.rows.addRow(follow, halfWidth);
		this.rows.addRow(junction, cooldown);

		this.rows.addHeader(Component.translatable("voxvelo_fitness_lib.road.section.blocks"));
		// One line that explains the list, or says why the last block could not be added.
		this.rows.addText((graphics, font, left, top) -> {
			Component line = this.error != null ? this.error : Component.translatable("voxvelo_fitness_lib.road.hint");
			graphics.centeredText(font, line, left + WIDTH / 2, top + 2, this.error != null ? ERROR : HINT);
		});
		EditBox input = new EditBox(this.font, 0, 0, HALF, 20, Component.translatable("voxvelo_fitness_lib.road.input"));
		input.setTooltip(Tips.of("voxvelo_fitness_lib.road.input"));
		input.setHint(Component.translatable("voxvelo_fitness_lib.road.input_hint"));
		input.setMaxLength(80);
		input.setValue(this.inputText);
		input.setResponder(text -> {
			this.inputText = text;
			this.error = null;
		});
		this.rows.addRow(input, Button.builder(Component.translatable("voxvelo_fitness_lib.road.add"), button -> {
			if (this.add(this.inputText)) {
				this.inputText = "";
				this.rebuildWidgets();
			}
		}).tooltip(Tips.of("voxvelo_fitness_lib.road.add")).bounds(RIGHT, 0, HALF, 20).build());

		boolean inWorld = this.minecraft.level != null && this.minecraft.player != null;
		Button under = Button.builder(Component.translatable("voxvelo_fitness_lib.road.add_under"), button -> this.addPicked(this.blockUnderPlayer(), "voxvelo_fitness_lib.road.nothing_under"))
			.tooltip(Tips.of("voxvelo_fitness_lib.road.add_under"))
			.bounds(0, 0, HALF, 20).build();
		Button looking = Button.builder(Component.translatable("voxvelo_fitness_lib.road.add_looking"), button -> this.addPicked(this.blockLookedAt(), "voxvelo_fitness_lib.road.not_looking"))
			.tooltip(Tips.of("voxvelo_fitness_lib.road.add_looking"))
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
		this.rows.addRow(Button.builder(Component.translatable("voxvelo_fitness_lib.road.reset"), button -> {
			this.ctx.config.roadBlocks = new ArrayList<>(FitnessConfig.DEFAULT_ROAD_BLOCKS);
			this.error = null;
			this.rebuildWidgets();
		}).tooltip(Tips.of("voxvelo_fitness_lib.road.reset")).bounds(0, 0, WIDTH, 20).build());

		this.rows.addHeader(Component.translatable("voxvelo_fitness_lib.road.section.terrain"));
		this.rows.addRow(new ValueSlider(0, 0, WIDTH, 10, 40, 1, this.ctx.config.slopeWindowBlocks,
			v -> Component.translatable("voxvelo_fitness_lib.config.slope_window", Math.round(v)),
			v -> this.ctx.config.slopeWindowBlocks = (int) Math.round(v)).tip("voxvelo_fitness_lib.config.slope_window"));

		this.rows.addHeader(Component.translatable("voxvelo_fitness.camera.section"));
		this.rows.addRow(CycleButton.onOffBuilder(this.ctx.config.bikeCameraFollow)
			.withTooltip(value -> Tips.of("voxvelo_fitness.camera.follow"))
			.create(0, 0, WIDTH, 20, Component.translatable("voxvelo_fitness.camera.follow"),
				(button, value) -> this.ctx.config.bikeCameraFollow = value));
	}

	/** The road options only matter with road follow on, and the cooldown only with the junction prompt on. */
	private void updateDependents(ValueSlider halfWidth, CycleButton<Boolean> junction, ValueSlider cooldown) {
		boolean following = this.ctx.config.roadFollowEnabled;
		halfWidth.active = following;
		junction.active = following;
		cooldown.active = following && this.ctx.config.junctionPrompt;
	}

	/** A listed road block, by its name in the game; pressing it takes the block off the list. */
	private Button entry(int x, String entry) {
		return Button.builder(Component.translatable("voxvelo_fitness_lib.road.entry", displayName(entry)), button -> this.remove(entry))
			.bounds(x, 0, HALF, 20)
			.tooltip(Tooltip.create(Component.translatable("voxvelo_fitness_lib.road.remove", entry)))
			.build();
	}

	/** The block's translated name, or the shortened id for a tag or a block the game does not know. */
	private static Component displayName(String entry) {
		Identifier id = entry.startsWith("#") ? null : Identifier.tryParse(entry);
		Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
		return block.<Component>map(Block::getName).orElseGet(() -> Component.literal(shorten(entry)));
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
			this.error = Component.translatable("voxvelo_fitness_lib.road.unknown");
			return false;
		}
		if (this.ctx.config.roadBlocks.contains(entry)) {
			this.error = Component.translatable("voxvelo_fitness_lib.road.already");
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
