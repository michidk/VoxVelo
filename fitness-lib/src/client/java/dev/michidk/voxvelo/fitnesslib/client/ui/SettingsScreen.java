package dev.michidk.voxvelo.fitnesslib.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A settings page in the layout of the vanilla options screens: the title on top, Done at the bottom and a list in
 * between that scrolls when the window is too small for it. Pages fill {@link #rows} in {@link #addOptions()} and
 * write their settings in {@link #save()}, which runs when the page closes.
 */
public abstract class SettingsScreen extends OptionsSubScreen {
	/** Text colours used on the pages: hints, live status, and problems. */
	public static final int HINT = 0xFF909090;
	public static final int STATUS = 0xFFC0C0C0;
	public static final int ERROR = 0xFFFF8080;

	protected SettingsList rows;

	protected SettingsScreen(@Nullable Screen parent, Component title) {
		super(parent, Minecraft.getInstance().options, title);
	}

	@Override
	protected void addContents() {
		this.rows = this.layout.addToContents(new SettingsList(this.minecraft, this.width, this));
		this.list = this.rows;
		this.addOptions();
	}

	/** Writes this page's settings; called when the page closes. */
	protected abstract void save();

	/** Builds the page again after what it shows changed, keeping the scroll position. */
	@Override
	protected void rebuildWidgets() {
		double scroll = this.rows == null ? 0.0 : this.rows.scrollAmount();
		this.layout.removeChildren();
		super.rebuildWidgets();
		this.rows.setScrollAmount(scroll);
	}

	@Override
	public void onClose() {
		this.save();
		super.onClose();
	}
}
