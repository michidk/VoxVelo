package dev.michidk.voxelvelo.client.ui;

import dev.michidk.voxelvelo.client.ClientContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The VoxelVelo settings hub, opened with the "Open Bike Settings" key. It leads to one page per topic: the
 * keyboard and the rider are built in, add-ons such as the fitness integration add their own pages and status lines.
 */
public class ConfigScreen extends Screen {
	private static final int WIDTH = 310;

	private final @Nullable Screen parent;
	private final ClientContext ctx = ClientContext.get();

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("voxelvelo.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - WIDTH / 2;
		int half = (WIDTH - 10) / 2;
		int right = left + half + 10;
		int y = 36 + this.ctx.statusLines().size() * 12 + (this.ctx.statusLines().isEmpty() ? 4 : 16);

		List<ClientContext.SettingsPage> pages = new ArrayList<>();
		pages.add(new ClientContext.SettingsPage("voxelvelo.config.keyboard", KeyboardScreen::new));
		pages.addAll(this.ctx.settingsPages());
		pages.add(new ClientContext.SettingsPage("voxelvelo.config.rider", RiderScreen::new));

		// Two buttons per row; a lone last one takes the whole row.
		for (int i = 0; i < pages.size(); i += 2) {
			boolean last = i == pages.size() - 1;
			this.page(left, y, last ? WIDTH : half, pages.get(i));
			if (!last) {
				this.page(right, y, half, pages.get(i + 1));
			}
			y += 24;
		}

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
			.bounds(left, this.height - 30, WIDTH, 20).build());
	}

	/** Adds a button that opens another settings page, built when the button is pressed. */
	private void page(int x, int y, int width, ClientContext.SettingsPage page) {
		Function<Screen, Screen> factory = page.factory();
		this.addRenderableWidget(Button.builder(Component.translatable(page.labelKey()), button -> this.minecraft.gui.setScreen(factory.apply(this)))
			.bounds(x, y, width, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 20, 0xFFFFFFFF);
		int y = 36;
		for (var line : this.ctx.statusLines()) {
			graphics.centeredText(this.font, line.get(), cx, y, 0xFFC0C0C0);
			y += 12;
		}
	}

	@Override
	public void onClose() {
		this.ctx.config.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
