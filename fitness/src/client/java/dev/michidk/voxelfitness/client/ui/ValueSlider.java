package dev.michidk.voxelfitness.client.ui;

import java.util.function.DoubleConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over a numeric range that snaps to a step, with a caller-supplied label and change callback. */
public final class ValueSlider extends AbstractSliderButton {
	private final double min;
	private final double max;
	private final double step;
	private final Function<Double, Component> label;
	private final DoubleConsumer onChange;

	public ValueSlider(int x, int y, int width, double min, double max, double step, double initial,
		Function<Double, Component> label, DoubleConsumer onChange) {
		super(x, y, width, 20, Component.empty(), (Math.max(min, Math.min(max, initial)) - min) / (max - min));
		this.min = min;
		this.max = max;
		this.step = step;
		this.label = label;
		this.onChange = onChange;
		this.updateMessage();
	}

	/** Shows the tooltip of the control labelled with {@code labelKey}, see {@link Tips}. */
	public ValueSlider tip(String labelKey) {
		this.setTooltip(Tips.of(labelKey));
		return this;
	}

	private double current() {
		double raw = this.min + this.value * (this.max - this.min);
		return Math.max(this.min, Math.min(this.max, Math.round(raw / this.step) * this.step));
	}

	@Override
	protected void updateMessage() {
		this.setMessage(this.label.apply(this.current()));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept(this.current());
	}
}
