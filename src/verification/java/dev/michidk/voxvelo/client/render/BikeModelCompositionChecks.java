package dev.michidk.voxvelo.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verifies that generated frame attachments and standalone bike components still share one exact pivot contract. */
public final class BikeModelCompositionChecks {
	private static final Path MODEL_ROOT = Path.of(
			"src/main/resources/assets/voxvelo/geckolib/models/entity");
	private static final String[] BIKES = {"road_bike", "gravel_bike", "mountain_bike"};
	private static final String[] PARTS = {"front_wheel", "rear_wheel", "handlebar"};

	private BikeModelCompositionChecks() {}

	public static void main(String[] args) throws IOException {
		for (String bike : BIKES) {
			JsonObject source = geometry(MODEL_ROOT.resolve("bikes/" + bike + ".geo.json"));
			JsonObject frame = geometry(MODEL_ROOT.resolve("bike_frames/" + bike + ".geo.json"));

			for (String part : PARTS) {
				JsonObject sourceBone = bone(source, part);
				JsonObject anchor = bone(frame, part);
				JsonObject componentGeometry = geometry(
						MODEL_ROOT.resolve("bike_parts/" + bike + "_" + part + ".geo.json"));
				JsonArray componentBones = componentGeometry.getAsJsonArray("bones");
				check(!componentBones.isEmpty(), bike + " " + part + " must have a component root");
				check(componentBones.size() == subtreeSize(source, part),
						bike + " " + part + " component did not extract its complete bone subtree");
				check(subtreeSize(frame, part) == 1,
						bike + " " + part + " frame attachment still owns child geometry");
				JsonObject component = componentBones.get(0).getAsJsonObject();

				check(!anchor.has("cubes") || anchor.getAsJsonArray("cubes").isEmpty(),
						bike + " " + part + " frame attachment still contains geometry");
				check("component".equals(component.get("name").getAsString()),
						bike + " " + part + " component root has the wrong name");
				check(!component.has("parent"), bike + " " + part + " component must not inherit a donor frame");
				check(sourceBone.get("pivot").equals(anchor.get("pivot")),
						bike + " " + part + " frame attachment pivot drifted from its source");
				check(sourceBone.get("pivot").equals(component.get("pivot")),
						bike + " " + part + " component pivot drifted from its attachment");
				check(sourceBone.get("cubes").equals(component.get("cubes")),
						bike + " " + part + " component geometry drifted from its source");
			}
		}
	}

	private static JsonObject geometry(Path path) throws IOException {
		try (var reader = Files.newBufferedReader(path)) {
			return JsonParser.parseReader(reader).getAsJsonObject()
					.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		}
	}

	private static JsonObject bone(JsonObject geometry, String name) {
		for (JsonElement element : geometry.getAsJsonArray("bones")) {
			JsonObject bone = element.getAsJsonObject();
			if (name.equals(bone.get("name").getAsString())) return bone;
		}
		throw new AssertionError("Missing bone " + name);
	}

	private static int subtreeSize(JsonObject geometry, String root) {
		java.util.Set<String> names = new java.util.HashSet<>();
		names.add(root);
		boolean changed;
		do {
			changed = false;
			for (JsonElement element : geometry.getAsJsonArray("bones")) {
				JsonObject bone = element.getAsJsonObject();
				if (bone.has("parent") && names.contains(bone.get("parent").getAsString())) {
					changed |= names.add(bone.get("name").getAsString());
				}
			}
		} while (changed);
		return names.size();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
