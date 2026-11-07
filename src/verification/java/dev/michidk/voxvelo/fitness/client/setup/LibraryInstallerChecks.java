package dev.michidk.voxvelo.fitness.client.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises installation and rejection using actual jar files without making network requests. */
public final class LibraryInstallerChecks {
	public static void main(String[] args) throws Exception {
		Path directory = Files.createTempDirectory("voxvelo-installer-checks");
		try {
			Path source = directory.resolve("library.download");
			Path target = directory.resolve("library.jar");
			jar(source, "voxvelo_fitness_lib", "0.1.2");
			long size = Files.size(source);
			String checksum = checksum(source);
			reject(() -> LibraryInstaller.verifyAndInstall(source, target, "0.1.2", size + 1, checksum));
			reject(() -> LibraryInstaller.verifyAndInstall(source, target, "0.1.2", size, "0".repeat(64)));
			reject(() -> LibraryInstaller.verifyAndInstall(source, target, "0.1.3", size, checksum));
			require(!Files.exists(target), "failed validation leaves no installable jar");
			LibraryInstaller.verifyAndInstall(source, target, "0.1.2", size, checksum);
			require(!Files.exists(source) && Files.exists(target), "successful validation installs jar");
			Files.copy(target, source);
			reject(() -> LibraryInstaller.verifyAndInstall(source, target, "0.1.2", size, checksum));
			require(checksum(target).equals(checksum), "existing jar is never overwritten");
			jar(source, "unrelated_mod", "0.1.2");
			reject(() -> LibraryInstaller.verifyAndInstall(source, directory.resolve("wrong.jar"), "0.1.2", Files.size(source), checksum(source)));
			try {
				LibraryInstaller.releasePage("../../other-project");
				throw new AssertionError("unsafe version accepted");
			} catch (IllegalArgumentException expected) {
				// Release selection must stay in the known repository.
			}
			if (args.length > 0) {
				Path downloaded = LibraryInstaller.install(directory.resolve("network"), args[0]);
				require(Files.exists(downloaded), "real matching release installs successfully");
			}
		} finally {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		System.out.println("Library checksum, identity, version, partial download and overwrite checks passed.");
	}

	private static void jar(Path path, String id, String version) throws IOException {
		try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"id\":\"" + id + "\",\"version\":\"" + version + "\"}").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
	}

	private static String checksum(Path path) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	@FunctionalInterface
	private interface Operation { void run() throws Exception; }

	private static void reject(Operation operation) throws Exception {
		try {
			operation.run();
			throw new AssertionError("invalid install was accepted");
		} catch (IOException expected) {
			// Validation failures must not install an executable jar.
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
