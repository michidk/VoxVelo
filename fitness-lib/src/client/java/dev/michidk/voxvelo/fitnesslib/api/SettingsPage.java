package dev.michidk.voxvelo.fitnesslib.api;

import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;

/**
 * A page of the fitness settings, shown as a button labelled {@code labelKey}; its tooltip is the translation of
 * {@code labelKey + ".tip"}. The factory receives the screen to return to.
 */
public record SettingsPage(String labelKey, Function<Screen, Screen> factory) {}
