package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import dev.michidk.voxelvelo.client.input.BikeInputSource;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses, for each control, which input source drives it. Sources can be mixed freely. */
public class SourcesScreen extends Screen {
	private static final int WIDTH = 310;

	private final Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public SourcesScreen(Screen parent) {
		super(Component.translatable("voxelvelo.sources.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int y = 60;
		for (ControlChannel channel : ControlChannel.values()) {
			List<BikeInputSource> choices = new ArrayList<>();
			for (BikeInputSource source : this.ctx.inputManager.sources()) {
				if (source.supports(channel)) {
					choices.add(source);
				}
			}
			this.addRenderableWidget(CycleButton.<BikeInputSource>builder(source -> Component.literal(source.displayName()), this.ctx.inputManager.selected(channel))
				.withValues(choices)
				.create(left, y, WIDTH, 20, Component.translatable("voxelvelo.sources." + channel.name().toLowerCase()), (button, value) -> this.ctx.config.setSource(channel, value.id())));
			y += 24;
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		graphics.centeredText(this.font, ConfigScreen.trainerStatus(this.ctx), cx, 36, 0xFFC0C0C0);
		graphics.centeredText(this.font, ConfigScreen.obcStatus(this.ctx), cx, 48, 0xFFC0C0C0);
		graphics.centeredText(this.font, Component.translatable("voxelvelo.sources.note"), cx, this.height - 58, 0xFF909090);
		graphics.centeredText(this.font, Component.translatable("voxelvelo.sources.note2"), cx, this.height - 46, 0xFF909090);
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
