package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class TrustStepTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Pays Jackson's one-time cold-start cost under the looser lifecycle-method
	 * timeout so the trust-writing {@code @Test} does not flake against
	 * surefire's 900ms per-test timeout when it runs first in a fresh JVM.
	 */
	@BeforeAll
	static void warmUpJackson(@TempDir Path dir) {
		new ClaudeTrustStore(dir.resolve(".claude.json")).trust(dir.resolve("repo"));
	}

	@Test
	void trustsTheCheckoutDirectory(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git", dir);
		new TrustStep(new ClaudeTrustStore(config)).execute(context, new RecordingCommandRunner());
		String key = context.repositoryDirectory().toAbsolutePath().normalize().toString();
		assertTrue(MAPPER.readTree(config.toFile()).path("projects").path(key)
				.path("hasTrustDialogAccepted").asBoolean());
	}

	@Test
	void isNamedTrust() {
		assertEquals("trust", new TrustStep(new ClaudeTrustStore()).name());
	}
}
