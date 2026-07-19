package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ClaudeTrustStoreTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Jackson's first databind read/write pays a one-time, reflection-heavy
	 * initialization cost. Charging that cold start to whichever {@code @Test}
	 * happens to run first makes it flake against surefire's 900ms per-test
	 * timeout, so pay it once here — a full trust cycle through the real path —
	 * under the looser lifecycle-method timeout instead.
	 */
	@BeforeAll
	static void warmUpJackson(@TempDir Path dir) {
		new ClaudeTrustStore(dir.resolve(".claude.json")).trust(dir.resolve("repo"));
	}

	private JsonNode read(Path config) throws IOException {
		return MAPPER.readTree(config.toFile());
	}

	private boolean trusted(JsonNode root, Path directory) {
		return root.path("projects").path(directory.toAbsolutePath().normalize().toString())
				.path("hasTrustDialogAccepted").asBoolean(false);
	}

	@Test
	void createsConfigAndTrustsDirectoryWhenFileMissing(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Path repo = dir.resolve("workspace/repo");
		assertTrue(new ClaudeTrustStore(config).trust(repo));
		assertTrue(Files.isRegularFile(config));
		assertTrue(trusted(read(config), repo));
	}

	@Test
	void isIdempotentWhenAlreadyTrusted(@TempDir Path dir) {
		Path config = dir.resolve(".claude.json");
		Path repo = dir.resolve("repo");
		ClaudeTrustStore store = new ClaudeTrustStore(config);
		assertTrue(store.trust(repo));
		assertFalse(store.trust(repo));
	}

	@Test
	void preservesExistingProjectsAndTopLevelFields(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.writeString(config, """
				{
				  "userID" : "abc",
				  "projects" : {
				    "/other/project" : { "hasTrustDialogAccepted" : true }
				  }
				}
				""");
		Path repo = dir.resolve("repo");
		assertTrue(new ClaudeTrustStore(config).trust(repo));
		JsonNode root = read(config);
		assertEquals("abc", root.path("userID").asText());
		assertTrue(root.path("projects").path("/other/project").path("hasTrustDialogAccepted").asBoolean());
		assertTrue(trusted(root, repo));
	}

	@Test
	void flipsAnExistingUntrustedEntryKeepingItsOtherFields(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Path repo = dir.resolve("repo");
		String key = repo.toAbsolutePath().normalize().toString();
		ObjectNode root = MAPPER.createObjectNode();
		ObjectNode entry = root.putObject("projects").putObject(key);
		entry.put("hasTrustDialogAccepted", false);
		entry.put("projectOnboardingSeenCount", 3);
		MAPPER.writerWithDefaultPrettyPrinter().writeValue(config.toFile(), root);

		assertTrue(new ClaudeTrustStore(config).trust(repo));

		JsonNode saved = read(config).path("projects").path(key);
		assertTrue(saved.path("hasTrustDialogAccepted").asBoolean());
		assertEquals(3, saved.path("projectOnboardingSeenCount").asInt());
	}

	@Test
	void refusesToOverwriteAConfigThatIsValidJsonButNotAnObject(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		String original = "[\"do not lose me\"]";
		Files.writeString(config, original);

		assertThrows(AdoptionException.class, () -> new ClaudeTrustStore(config).trust(dir.resolve("repo")));
		assertEquals(original, Files.readString(config));
	}

	@Test
	void treatsAnEmptyConfigFileAsAFreshObject(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.writeString(config, "");
		Path repo = dir.resolve("repo");

		assertTrue(new ClaudeTrustStore(config).trust(repo));
		assertTrue(trusted(read(config), repo));
	}
}
