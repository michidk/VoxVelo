package dev.michidk.voxvelo.bikes.packaging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/**
 * Verifies artifact ownership and the dependency direction independently of development classpaths.
 */
public final class BuildLayoutChecks {
	public static void main(String[] args) throws Exception {
		try (ZipFile adapter = new ZipFile(args[0]);
				ZipFile core = new ZipFile(args[1]);
				ZipFile fitness = new ZipFile(args[2])) {
			checkSources(core, "bikes/src/main/java", adapter, fitness);
			checkSources(core, "bikes/src/client/java", adapter, fitness);
			checkSources(adapter, "fitness/src/main/java", core, fitness);
			checkSources(adapter, "fitness/src/client/java", core, fitness);
			checkSources(fitness, "fitness-lib/src/client/java", core, adapter);
			checkBoundary(core, "dev/michidk/voxvelo/fitness/", "dev/michidk/voxvelo/fitnesslib/", "org/jmdns/");
			checkBoundary(fitness, "dev/michidk/voxvelo/bikes/", "dev/michidk/voxvelo/fitness/", "com/geckolib/");
			for (ZipFile owner : new ZipFile[] {core, adapter, fitness}) {
				owner.stream()
						.filter(e -> !e.isDirectory())
						.filter(
								e ->
										e.getName().endsWith(".class")
												|| e.getName().startsWith("assets/")
												|| e.getName().startsWith("data/")
												|| e.getName().endsWith(".mixins.json"))
						.forEach(
								e -> {
									for (ZipFile other : new ZipFile[] {core, adapter, fitness})
										if (other != owner)
											require(
													other.getEntry(e.getName()) == null,
													"Duplicate entry: " + e.getName());
								});
			}
			JsonObject c = json(core, "fabric.mod.json"),
					a = json(adapter, "fabric.mod.json"),
					f = json(fitness, "fabric.mod.json");
			require(c.get("id").getAsString().equals("voxvelo_bikes"), "core mod ID");
			require(a.get("id").getAsString().equals("voxvelo_fitness"), "adapter mod ID");
			require(f.get("id").getAsString().equals("voxvelo_fitness_lib"), "generic mod ID");
			require(
					a.getAsJsonObject("depends")
							.get("voxvelo_bikes")
							.getAsString()
							.equals("=" + c.get("version").getAsString()),
					"adapter requires matching core");
			require(
					a.getAsJsonObject("depends")
							.get("voxvelo_fitness_lib")
							.getAsString()
							.equals("=" + f.get("version").getAsString()),
					"adapter requires matching fitness");
			require(
					!f.getAsJsonObject("depends").has("voxvelo_bikes")
							&& !f.getAsJsonObject("depends").has("voxvelo_fitness")
							&& !f.getAsJsonObject("depends").has("geckolib"),
					"generic mod is independent of bike/animation mods");
			require(
					!c.getAsJsonObject("depends").has("voxvelo_fitness_lib")
							&& !c.getAsJsonObject("depends").has("voxvelo_fitness"),
					"core is independent of fitness");
			require(!a.has("jars") && !c.has("jars"), "Bikes and Fitness do not bundle the library or each other");
			for (ZipFile jar : new ZipFile[] {core, adapter, fitness}) {
				JsonObject metadata = json(jar, "fabric.mod.json");
				require(jar.getEntry(metadata.get("icon").getAsString()) != null, "Mod icon is packaged");
				for (var entry : metadata.getAsJsonObject("entrypoints").entrySet()) {
					for (var value : entry.getValue().getAsJsonArray()) {
						require(jar.getEntry(value.getAsString().replace('.', '/') + ".class") != null,
								"Entrypoint class is packaged: " + value.getAsString());
					}
				}
			}
			require(
					a.getAsJsonObject("entrypoints").has("voxvelo_bikes-client-extension")
							&& !a.has("mixins"),
					"adapter uses core hooks");
			require(
					f.getAsJsonObject("entrypoints").has("client"),
					"generic fitness starts without core");
			JsonObject coreLanguage = json(core, "assets/voxvelo_bikes/lang/en_us.json"),
					fitnessLanguage = json(fitness, "assets/voxvelo_fitness_lib/lang/en_us.json");
			require(
					coreLanguage.keySet().stream().noneMatch(fitnessLanguage::has),
					"translations don't overwrite core");
			for (ZipFile jar : new ZipFile[] {core, adapter})
				require(
						jar.stream()
								.noneMatch(
										e ->
												e.getName().startsWith("natives/")
														|| e.getName().contains("jmdns")),
						"device libraries only belong to generic fitness");
			for (String platform : System.getProperty("voxvelo_fitness_lib.requiredNatives", "").split(",")) {
				if (!platform.isBlank())
					require(
							fitness.stream()
									.anyMatch(
											e ->
													e.getName()
																	.startsWith(
																			"natives/"
																					+ platform
																							.trim()
																					+ "/")
															&& e.getSize() > 0),
							"Missing Bluetooth native: " + platform);
			}
		}
		System.out.println("Bikes, Fitness Library and Fitness jar layout and dependency boundaries passed.");
	}

	private static void checkBoundary(ZipFile jar, String... forbiddenPackages) throws Exception {
		for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
			try (var input = jar.getInputStream(entry)) {
				String constants = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
				for (String forbidden : forbiddenPackages) {
					require(!constants.contains(forbidden), jar.getName() + ": " + entry.getName() + " references " + forbidden);
				}
			}
		}
	}

	private static void checkSources(ZipFile owner, String directory, ZipFile... others)
			throws Exception {
		Path root = Path.of(directory);
		try (var files = Files.walk(root)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
				String name =
						root.relativize(file)
								.toString()
								.replace('\\', '/')
								.replace(".java", ".class");
				require(owner.getEntry(name) != null, "Missing owned class: " + name);
				for (ZipFile other : others)
					require(other.getEntry(name) == null, "Duplicated class: " + name);
			}
		}
	}

	private static JsonObject json(ZipFile jar, String name) throws Exception {
		require(jar.getEntry(name) != null, "Missing resource: " + name);
		try (var reader =
				new InputStreamReader(
						jar.getInputStream(jar.getEntry(name)), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
