package dev.michidk.voxvelo.client.ui;

import dev.michidk.voxvelo.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.client.fitness.FitnessContext;
import dev.michidk.voxvelo.client.road.RoadBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

/**
 * Settings for road following: whether it is on, which blocks count as road (a list the player edits) and how
 * wide a road may be. A block can be typed in, taken from under the player, or from the block being looked at.
 */
public class RoadScreen extends Screen {
	private static final int COLUMN = 150;
	private static final int WIDTH = 310;
	private static final int VISIBLE_ROWS = 7;
	private static final int ROW = 22;

	private final Screen parent;
	private final FitnessContext ctx = FitnessContext.get();
	private String inputText = "";
	private int page;

	public RoadScreen(Screen parent) {
		super(Component.translatable("voxvelo.road.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int right = left + WIDTH - COLUMN;

		List<String> blocks = this.ctx.config.roadBlocks;
		int pages = Math.max(1, (blocks.size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS);
		this.page = Math.max(0, Math.min(this.page, pages - 1));
		int y = 50;
		for (int i = this.page * VISIBLE_ROWS; i < Math.min(blocks.size(), (this.page + 1) * VISIBLE_ROWS); i++) {
			String entry = blocks.get(i);
			this.addRenderableWidget(Button.builder(Component.literal(shorten(entry)), button -> this.remove(entry))
				.bounds(left, y, COLUMN, 20)
				.tooltip(Tooltip.create(Component.translatable("voxvelo.road.remove", entry)))
				.build());
			y += ROW;
		}
		if (pages > 1) {
			int pageY = 50 + VISIBLE_ROWS * ROW;
			this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.turn(-1)).bounds(left, pageY, 70, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.turn(1)).bounds(left + COLUMN - 70, pageY, 70, 20).build());
		}

		y = 50;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.roadFollowEnabled)
			.withTooltip(value -> Tips.of("voxvelo.road.follow"))
			.create(right, y, COLUMN, 20, Component.translatable("voxvelo.road.follow"), (button, value) -> this.ctx.config.roadFollowEnabled = value));
		y += ROW + 6;
		EditBox input = this.addRenderableWidget(new EditBox(this.font, right, y, COLUMN, 20, Component.translatable("voxvelo.road.input")));
		input.setTooltip(Tips.of("voxvelo.road.input"));
		input.setHint(Component.translatable("voxvelo.road.input_hint"));
		input.setMaxLength(80);
		input.setValue(this.inputText);
		input.setResponder(text -> this.inputText = text);
		y += ROW;
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.road.add"), button -> {
			this.add(this.inputText);
			this.inputText = "";
		}).tooltip(Tips.of("voxvelo.road.add")).bounds(right, y, COLUMN, 20).build());
		y += ROW;
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.road.add_under"), button -> this.add(this.blockUnderPlayer()))
			.tooltip(Tips.of("voxvelo.road.add_under"))
			.bounds(right, y, COLUMN, 20).build());
		y += ROW;
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.road.add_looking"), button -> this.add(this.blockLookedAt()))
			.tooltip(Tips.of("voxvelo.road.add_looking"))
			.bounds(right, y, COLUMN, 20).build());
		y += ROW;
		this.addRenderableWidget(Button.builder(Component.translatable("voxvelo.road.reset"), button -> {
			this.ctx.config.roadBlocks = new ArrayList<>(FitnessConfig.DEFAULT_ROAD_BLOCKS);
			this.rebuildWidgets();
		}).tooltip(Tips.of("voxvelo.road.reset")).bounds(right, y, COLUMN, 20).build());
		y += ROW + 6;
		this.addRenderableWidget(new ValueSlider(right, y, COLUMN, 2.0, 16.0, 1.0, this.ctx.config.roadMaxHalfWidth,
			v -> Component.translatable("voxvelo.road.half_width", Math.round(v)), v -> this.ctx.config.roadMaxHalfWidth = (int) Math.round(v)).tip("voxvelo.road.half_width"));
		y += ROW + 6;
		this.addRenderableWidget(CycleButton.onOffBuilder(this.ctx.config.junctionPrompt)
			.withTooltip(value -> Tips.of("voxvelo.road.junction"))
			.create(right, y, COLUMN, 20, Component.translatable("voxvelo.road.junction"), (button, value) -> this.ctx.config.junctionPrompt = value));
		y += ROW;
		this.addRenderableWidget(new ValueSlider(right, y, COLUMN, 5.0, 120.0, 5.0, this.ctx.config.junctionCooldownSeconds,
			v -> Component.translatable("voxvelo.road.cooldown", Math.round(v)), v -> this.ctx.config.junctionCooldownSeconds = (int) Math.round(v)).tip("voxvelo.road.cooldown"));

		y += ROW;
		this.addRenderableWidget(new ValueSlider(right, y, COLUMN, 10, 40, 1, this.ctx.config.slopeWindowBlocks,
			v -> Component.translatable("voxvelo.config.slope_window", Math.round(v)),
			v -> this.ctx.config.slopeWindowBlocks = (int) Math.round(v)).tip("voxvelo.config.slope_window"));

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	private static String shorten(String entry) {
		return entry.startsWith("minecraft:") ? entry.substring("minecraft:".length()) : entry.startsWith("#minecraft:") ? "#" + entry.substring("#minecraft:".length()) : entry;
	}

	private void turn(int delta) {
		this.page += delta;
		this.rebuildWidgets();
	}

	private void remove(String entry) {
		List<String> list = new ArrayList<>(this.ctx.config.roadBlocks);
		list.remove(entry);
		this.ctx.config.roadBlocks = list;
		this.rebuildWidgets();
	}

	/** Adds a block id or #tag, completing a missing namespace, if the game knows it and it is not listed yet. */
	private void add(String raw) {
		String entry = normalize(raw);
		if (entry == null || !RoadBlocks.isKnown(entry)) {
			this.tell("voxvelo.road.unknown");
			return;
		}
		if (this.ctx.config.roadBlocks.contains(entry)) {
			this.tell("voxvelo.road.already");
			return;
		}
		List<String> list = new ArrayList<>(this.ctx.config.roadBlocks);
		list.add(entry);
		this.ctx.config.roadBlocks = FitnessConfig.sanitizeBlocks(list);
		this.page = Integer.MAX_VALUE;
		this.rebuildWidgets();
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

	private String blockUnderPlayer() {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return null;
		}
		BlockPos pos = BlockPos.containing(this.minecraft.player.getX(), this.minecraft.player.getY() - 0.2, this.minecraft.player.getZ());
		BlockState state = this.minecraft.level.getBlockState(pos);
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private String blockLookedAt() {
		if (this.minecraft.level != null && this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return BuiltInRegistries.BLOCK.getKey(this.minecraft.level.getBlockState(hit.getBlockPos()).getBlock()).toString();
		}
		return null;
	}

	private void tell(String key) {
		if (this.minecraft.player != null) {
			this.minecraft.player.sendOverlayMessage(Component.translatable(key));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("voxvelo.road.hint"), cx, 34, 0xFF909090);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
