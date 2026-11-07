package dev.michidk.voxvelo.fitness.client.setup;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Installs an explicitly requested matching release without loading code into the running game. */
public final class LibraryInstaller {
	private static final long MAX_BYTES = 64L * 1024 * 1024;
	private static final Duration TIMEOUT = Duration.ofMinutes(2);
	private static final String REPOSITORY = "https://github.com/michidk/VoxVelo";
	private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(
		Thread.ofPlatform().daemon(true).name("voxvelo-download-timeout").factory());

	private LibraryInstaller() {}

	public static URI releasePage(String version) {
		validateVersion(version);
		return URI.create(REPOSITORY + "/releases/tag/v" + version);
	}

	private static void validateVersion(String version) {
		if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?")) {
			throw new IllegalArgumentException("Unsupported release version");
		}
	}

	/** Fetches release metadata only after the user requests installation. */
	public static Path install(Path mods, String version) throws IOException, InterruptedException {
		validateVersion(version);
		String name = "voxvelo-fitness-lib-" + version + ".jar";
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
			.followRedirects(HttpClient.Redirect.NORMAL).build()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/michidk/VoxVelo/releases/tags/v" + version))
				.timeout(TIMEOUT).header("Accept", "application/vnd.github+json").header("User-Agent", "VoxVelo-Setup/" + version).build();
			HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			JsonObject release;
			try (InputStream input = response.body()) {
				ScheduledFuture<?> deadline = closeAfterTimeout(input);
				try {
					if (response.statusCode() == 404) throw new IOException("The matching library release is not published yet.");
					if (response.statusCode() != 200) throw new IOException("GitHub could not provide this release. Please try again later (HTTP " + response.statusCode() + ").");
					byte[] metadata = input.readNBytes(2 * 1024 * 1024 + 1);
					if (metadata.length > 2 * 1024 * 1024) throw new IOException("Release response is too large.");
					release = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
				} finally {
					deadline.cancel(false);
				}
			}
			if (!release.get("tag_name").getAsString().equals("v" + version)) throw new IOException("Release version does not match.");
			JsonObject asset = null;
			for (var entry : release.getAsJsonArray("assets")) {
				JsonObject candidate = entry.getAsJsonObject();
				if (candidate.get("name").getAsString().equals(name)) asset = candidate;
			}
			if (asset == null) throw new IOException("The matching library has not been published on GitHub yet.");
			String expectedUrl = REPOSITORY + "/releases/download/v" + version + "/" + name;
			if (!asset.get("browser_download_url").getAsString().equals(expectedUrl)) throw new IOException("Unexpected library download location.");
			long size = asset.get("size").getAsLong();
			String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : "";
			if (size <= 0 || size > MAX_BYTES || !digest.matches("sha256:[a-fA-F0-9]{64}")) {
				throw new IOException("This release has no usable size/checksum. Use the manual download instead.");
			}
			Files.createDirectories(mods);
			Path temporary = Files.createTempFile(mods, ".voxvelo-library-", ".download");
			try {
				HttpResponse<InputStream> download = client.send(HttpRequest.newBuilder(URI.create(expectedUrl))
					.timeout(TIMEOUT).header("User-Agent", "VoxVelo-Setup/" + version).build(), HttpResponse.BodyHandlers.ofInputStream());
				try (InputStream input = download.body(); var output = Files.newOutputStream(temporary)) {
					ScheduledFuture<?> deadline = closeAfterTimeout(input);
					try {
						if (download.statusCode() != 200) throw new IOException("Download failed (HTTP " + download.statusCode() + ").");
						byte[] buffer = new byte[8192];
						long total = 0;
						for (int read; (read = input.read(buffer)) != -1;) {
							total += read;
							if (total > size) throw new IOException("Download exceeds its expected size.");
							output.write(buffer, 0, read);
						}
					} finally {
						deadline.cancel(false);
					}
				}
				return verifyAndInstall(temporary, mods.resolve(name), version, size, digest.substring(7));
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
	}

	/** HttpClient's streaming body outlives the request timeout, so bound stalled reads as well. */
	private static ScheduledFuture<?> closeAfterTimeout(InputStream input) {
		return DEADLINES.schedule(() -> {
			try {
				input.close();
			} catch (IOException ignored) {
				// Closing the stream wakes the background installer; the UI offers a retry.
			}
		}, TIMEOUT.toSeconds(), TimeUnit.SECONDS);
	}

	/** Rejects partial, modified, or unrelated files before making a jar visible to Fabric. */
	public static Path verifyAndInstall(Path temporary, Path destination, String version, long size, String sha256) throws IOException {
		if (Files.size(temporary) != size) throw new IOException("Download is incomplete. Please try again.");
		try (InputStream input = Files.newInputStream(temporary)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
			if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(sha256)) throw new IOException("Library checksum did not match. Please try again.");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try (ZipFile jar = new ZipFile(temporary.toFile())) {
			var entry = jar.getEntry("fabric.mod.json");
			if (entry == null || entry.getSize() > 64 * 1024) throw new IOException("Download is not a Fitness Library mod.");
			try (InputStream input = jar.getInputStream(entry)) {
				JsonObject metadata = JsonParser.parseString(new String(input.readNBytes(64 * 1024), StandardCharsets.UTF_8)).getAsJsonObject();
				if (!metadata.get("id").getAsString().equals("voxvelo_fitness_lib") || !metadata.get("version").getAsString().equals(version)) {
					throw new IOException("Downloaded mod identity/version does not match Fitness.");
				}
			}
		}
		// No replacement: never clobber a file another installer or the player has created.
		return Files.move(temporary, destination);
	}
}
