package dev.michidk.voxvelo.bikes.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Standalone checks that every recipe can appear in the vanilla recipe book and that survival players learn it: a
 * book-visible (non-special) type, and an unlock advancement that grants it once one of its ingredients is owned.
 */
public final class RecipeBookChecks {
	private static final Path DATA = Path.of("bikes/src/main/resources/data/voxvelo_bikes");
	private static final Path ITEM_MODELS = Path.of("bikes/src/main/resources/assets/voxvelo_bikes/items");
	/** Types that build a recipe-book display. Special types (CustomRecipe) never reach the book. */
	private static final Set<String> BOOK_TYPES = Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless",
			"minecraft:crafting_dye", "voxvelo_bikes:bike_assembly", "voxvelo_bikes:wheel_retread");

	public static void main(String[] args) throws IOException {
		Map<String, JsonObject> recipes = read(DATA.resolve("recipe"));
		Map<String, JsonObject> unlocks = read(DATA.resolve("advancement/recipes/equipment"));
		require(!recipes.isEmpty(), "recipes are found (run from the project directory)");

		Map<String, String> groupByResult = new HashMap<>();
		for (var entry : recipes.entrySet()) {
			String name = entry.getKey(), id = "voxvelo_bikes:" + name;
			JsonObject recipe = entry.getValue();
			String type = recipe.get("type").getAsString();
			require(BOOK_TYPES.contains(type), name + " has a recipe-book-visible type, not " + type);
			require(recipe.has("category"), name + " names its recipe-book tab");

			// Several recipes for one item are listed as one book entry only if they share a group.
			if (!type.equals("minecraft:crafting_dye") && recipe.has("result")) {
				String result = recipe.getAsJsonObject("result").get("id").getAsString();
				String group = recipe.has("group") ? recipe.get("group").getAsString() : "";
				String previous = groupByResult.putIfAbsent(result, group);
				require(previous == null || !group.isEmpty() && previous.equals(group),
						name + " shares a recipe-book group with the other recipes for " + result);
			}

			JsonObject unlock = unlocks.get(name);
			require(unlock != null, name + " has an unlock advancement in advancement/recipes/equipment");
			require(unlock.get("parent").getAsString().equals("minecraft:recipes/root"), name + " unlock hangs under recipes/root");
			require(unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").equals(array(id)), name + " unlock grants exactly " + id);

			JsonObject criteria = unlock.getAsJsonObject("criteria");
			JsonArray requirements = unlock.getAsJsonArray("requirements");
			require(requirements.size() == 1 && requirements.get(0).getAsJsonArray().size() == criteria.size(),
					name + " unlock fires on any one of its criteria");
			boolean recipeCriterion = false, itemCriterion = false;
			for (var criterion : criteria.entrySet()) {
				JsonObject value = criterion.getValue().getAsJsonObject();
				String trigger = value.get("trigger").getAsString();
				JsonObject conditions = value.getAsJsonObject("conditions");
				if (trigger.equals("minecraft:recipe_unlocked")) {
					recipeCriterion |= conditions.get("recipes").getAsString().equals(id);
				} else if (trigger.equals("minecraft:inventory_changed")) {
					itemCriterion = true;
					for (JsonElement predicate : conditions.getAsJsonArray("items")) {
						JsonElement items = predicate.getAsJsonObject().get("items");
						for (JsonElement item : items.isJsonArray() ? items.getAsJsonArray() : array(items.getAsString())) {
							String itemId = item.getAsString();
							if (itemId.startsWith("voxvelo_bikes:"))
								require(Files.exists(ITEM_MODELS.resolve(itemId.substring("voxvelo_bikes:".length()) + ".json")), name + " unlock item " + itemId + " exists");
						}
					}
				}
			}
			require(recipeCriterion, name + " unlock stops once " + id + " is known");
			require(itemCriterion, name + " unlock is triggered by owning an ingredient");
		}
		for (String name : unlocks.keySet()) require(recipes.containsKey(name), "unlock " + name + " has a recipe");
		System.out.println("Recipe book checks passed (" + recipes.size() + " recipes).");
	}

	private static Map<String, JsonObject> read(Path dir) throws IOException {
		Map<String, JsonObject> files = new HashMap<>();
		if (!Files.isDirectory(dir)) return files;
		try (Stream<Path> paths = Files.list(dir)) {
			for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
				String name = path.getFileName().toString();
				files.put(name.substring(0, name.length() - 5), JsonParser.parseString(Files.readString(path)).getAsJsonObject());
			}
		}
		return files;
	}

	private static JsonArray array(String... values) {
		JsonArray array = new JsonArray();
		List.of(values).forEach(array::add);
		return array;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
