package dev.michidk.voxvelo.fitnesslib.client.obc;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
/** Actions shared by controller buttons and Minecraft's rebindable controls. */
public final class ObcActions {
public record Action(int id, String key, KeyMapping mapping, Runnable run) {
public Component label() { return Component.translatable(this.key); }
}
private static final Map<Integer, Action> ACTIONS = new LinkedHashMap<>();
public static void register(int id, String key, KeyMapping mapping, Runnable run) {
if (ACTIONS.putIfAbsent(id, new Action(id, key, mapping, run)) != null) throw new IllegalArgumentException("Duplicate controller action");
}
public static java.util.List<Action> actions() { return java.util.List.copyOf(ACTIONS.values()); }
public static int[] supported() {
return java.util.stream.IntStream.concat(java.util.Arrays.stream(Obc.SUPPORTED_BUTTONS), ACTIONS.keySet().stream().mapToInt(Integer::intValue)).distinct().toArray();
}
public static void tick(ObcControls controls) {
var client = net.minecraft.client.Minecraft.getInstance();
for (int id : controls.consumeActionPresses()) {
Action action = ACTIONS.get(id);
if (action != null && client.level != null && client.gui.screen() == null) action.run.run();
}
for (Action action : actions()) while (action.mapping.consumeClick()) {
if (client.level != null && client.gui.screen() == null) action.run.run();
}
}
}
