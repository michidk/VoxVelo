package dev.michidk.voxvelo.fitnesslib.client.ui;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Tooltips for the settings controls. By convention the text of a control sits under its label key plus ".tip". */
public final class Tips {
	private Tips() {
	}

	/** The tooltip for the control labelled with {@code labelKey}. */
	public static Tooltip of(String labelKey) {
		return Tooltip.create(Component.translatable(labelKey + ".tip"));
	}
}
