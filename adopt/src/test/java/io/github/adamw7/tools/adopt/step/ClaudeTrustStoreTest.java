package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Stream;

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

	/**
	 * The files beside the config that the store is not entitled to leave behind.
	 * Its lock file is: it guards the read-modify-write against a concurrent
	 * adoption, so it has to outlive the update that took it — a lock file deleted
	 * on the way out is a lock the next process takes on a different inode.
	 */
	private List<Path> strayFiles(Path dir) throws IOException {
		try (Stream<Path> entries = Files.list(dir)) {
			return entries.filter(Files::isRegularFile)
					.filter(entry -> !entry.getFileName().toString().endsWith(".adopt-lock"))
					.toList();
		}
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

	/**
	 * The refusal has to reach inside the document too. A {@code projects} field
	 * carrying something other than an object was quietly replaced with a fresh one,
	 * discarding whatever it held — the very outcome the top-level check exists to
	 * prevent.
	 */
	@Test
	void refusesToOverwriteAProjectsFieldThatIsNotAnObject(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		String original = "{\"projects\":[\"do not lose me\"],\"numStartups\":4}";
		Files.writeString(config, original);

		assertThrows(AdoptionException.class, () -> new ClaudeTrustStore(config).trust(dir.resolve("repo")));
		assertEquals(original, Files.readString(config));
	}

	/** A {@code projects} field the config carries as JSON null is simply created. */
	@Test
	void treatsANullProjectsFieldAsAbsent(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.writeString(config, "{\"projects\":null,\"numStartups\":4}");
		Path repo = dir.resolve("repo");

		assertTrue(new ClaudeTrustStore(config).trust(repo));
		assertTrue(Files.readString(config).contains("hasTrustDialogAccepted"), Files.readString(config));
	}

	/**
	 * A half-written or hand-edited config is not something to silently replace:
	 * the trust flag is worth far less than the user's own Claude Code settings.
	 */
	@Test
	void refusesToOverwriteAConfigThatIsNotValidJson(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.writeString(config, "{\"projects\": ");
		ClaudeTrustStore store = new ClaudeTrustStore(config);
		Path repo = dir.resolve("repo");
		assertThrows(AdoptionException.class, () -> store.trust(repo));
		assertEquals("{\"projects\": ", Files.readString(config), "the unreadable config must be left as it was");
	}

	@Test
	void treatsAnEmptyConfigFileAsAFreshObject(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.writeString(config, "");
		Path repo = dir.resolve("repo");

		assertTrue(new ClaudeTrustStore(config).trust(repo));
		assertTrue(trusted(read(config), repo));
	}

	/**
	 * The config is replaced by a move, not truncated and rewritten in place, so a
	 * reader only ever sees one whole document. This pins the outcome that matters —
	 * nothing of the adoption's own scratch work is left beside the config for
	 * {@code claude} to trip over, and the config itself is complete.
	 */
	@Test
	void leavesNothingBehindBesideTheConfigItWrote(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Path repo = dir.resolve("repo");

		assertTrue(new ClaudeTrustStore(config).trust(repo));

		assertEquals(List.of(config), strayFiles(dir),
				"the temporary file the write goes through must not survive it");
		assertTrue(trusted(read(config), repo));
	}

	/**
	 * A write that cannot land takes its scratch file with it. Otherwise the failure
	 * left a stray half-configuration beside {@code ~/.claude.json}, and a batch
	 * retrying against the same home directory added one per attempt.
	 *
	 * <p>The move is made to fail by putting a non-empty directory where the config
	 * belongs: no filesystem renames a file over one, whatever the caller's
	 * privileges — unlike an unwritable directory, which does not stop {@code root}.
	 */
	@Test
	void leavesNothingBehindWhenTheWriteCannotLand(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Files.createDirectories(config.resolve("nested"));

		assertThrows(AdoptionException.class, () -> new ClaudeTrustStore(config).trust(dir.resolve("repo")));

		assertEquals(List.of(), strayFiles(dir), "the temporary file must not outlive the failed write");
	}

	/**
	 * Two adoptions trusting two checkouts at once must both be recorded. Each reads
	 * the whole document, adds its own directory, and writes the whole of it back, so
	 * without an exclusive section around that sequence the later write is built on a
	 * document read before the earlier one landed and silently drops it — leaving that
	 * adoption's {@code claude init} to block on the trust prompt until its command
	 * timeout. The barrier makes both threads read at the same moment, which is the
	 * interleaving that loses an update.
	 */
	@Test
	void concurrentTrustsAreBothRecorded(@TempDir Path dir) throws Exception {
		Path config = dir.resolve(".claude.json");
		Path first = dir.resolve("first");
		Path second = dir.resolve("second");
		CyclicBarrier startTogether = new CyclicBarrier(2);

		Thread firstThread = trusting(config, first, startTogether);
		Thread secondThread = trusting(config, second, startTogether);
		firstThread.join();
		secondThread.join();

		JsonNode root = read(config);
		assertTrue(trusted(root, first), "the first directory must survive the second adoption's write");
		assertTrue(trusted(root, second), "the second directory must survive the first adoption's write");
	}

	/**
	 * A {@code ClaudeTrustStore} with another writer standing in the window between
	 * the read the update is built on and the move that lands it. That writer is
	 * {@code claude} itself, which writes {@code ~/.claude.json} and takes no lock of
	 * the adoption's, so no lock can shut it out and only re-reading before the move
	 * notices it — the case these tests exist for.
	 *
	 * @param competitor what the other writer leaves behind, written once, after the
	 *                   store has read the configuration it is about to build on
	 */
	private static final class Contended extends ClaudeTrustStore {

		private final Path config;
		private final String competitor;
		private boolean pending = true;

		Contended(Path config, String competitor) {
			super(config);
			this.config = config;
			this.competitor = competitor;
		}

		@Override
		String readText() {
			String text = super.readText();
			if (pending) {
				pending = false;
				write(competitor);
			}
			return text;
		}

		private void write(String content) {
			try {
				Files.writeString(config, content);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * The update is built again on what the other writer left, rather than written
	 * over it. Both entries have to survive: the adoption's, or its {@code claude
	 * init} blocks on the trust prompt, and the other writer's, because this is
	 * Claude Code's own per-user state and discarding a session's work is exactly
	 * what the whole read-modify-write dance is for.
	 */
	@Test
	void keepsAConcurrentWritersEntryByBuildingTheUpdateAgain(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Path repo = dir.resolve("repo");
		Path elsewhere = dir.resolve("elsewhere");
		Files.writeString(config, "{}");

		assertTrue(new Contended(config, trustDocument(elsewhere)).trust(repo));

		JsonNode root = read(config);
		assertTrue(trusted(root, repo), "the adoption's own directory must be recorded");
		assertTrue(trusted(root, elsewhere), "the concurrent writer's entry must survive the retry");
		assertEquals(List.of(config), strayFiles(dir), "the stale attempt's temporary file must not survive it");
	}

	/**
	 * A configuration something is rewriting continuously is left alone rather than
	 * written over. Giving up is the safe answer: the adoption fails with a message
	 * naming the file, where overwriting it would silently discard whatever that
	 * writer had put there.
	 */
	@Test
	void refusesToOverwriteAConfigSomethingElseKeepsRewriting(@TempDir Path dir) throws IOException {
		Path config = dir.resolve(".claude.json");
		Path elsewhere = dir.resolve("elsewhere");
		Files.writeString(config, "{}");
		AlwaysContended store = new AlwaysContended(config, trustDocument(elsewhere));

		AdoptionException failure = assertThrows(AdoptionException.class, () -> store.trust(dir.resolve("repo")));

		assertTrue(failure.getMessage().contains(config.toString()), "the failure must name the config: " + failure);
		assertTrue(trusted(read(config), elsewhere), "the concurrent writer's document must be left as it was");
		assertEquals(List.of(config), strayFiles(dir), "no attempt's temporary file may survive it");
		assertEquals(2 * ClaudeTrustStore.WRITE_ATTEMPTS, store.reads(),
				"each attempt reads once to build on and once to check before the move, and there are"
						+ " WRITE_ATTEMPTS of them — an attempt more is another write aimed at a live file");
	}

	/** The other writer never stops, so every attempt reads a document the last one did not. */
	private static final class AlwaysContended extends ClaudeTrustStore {

		private final Path config;
		private final String competitor;
		private int writes;

		AlwaysContended(Path config, String competitor) {
			super(config);
			this.config = config;
			this.competitor = competitor;
		}

		/** How many reads the store made, which is two per attempt it gave the update. */
		int reads() {
			return writes;
		}

		@Override
		String readText() {
			String text = super.readText();
			rewrite();
			return text;
		}

		/**
		 * Each rewrite differs from the last, so the comparison before the move fails
		 * however the reads interleave — a writer that put back byte-for-byte what was
		 * there has changed nothing and is right to be let through.
		 */
		private void rewrite() {
			try {
				writes++;
				Files.writeString(config, competitor + " ".repeat(writes));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * Written through Jackson rather than concatenated, because the key is a path and
	 * a Windows one carries backslashes: {@code C:\Users\...} pasted into a JSON
	 * literal reads as the escapes {@code \U} and {@code \.}, so the document the
	 * competing writer is supposed to have left was not parseable JSON at all there.
	 */
	private String trustDocument(Path directory) {
		ObjectNode root = MAPPER.createObjectNode();
		root.putObject("projects").putObject(directory.toAbsolutePath().normalize().toString())
				.put("hasTrustDialogAccepted", true);
		return root.toString();
	}

	private Thread trusting(Path config, Path directory, CyclicBarrier startTogether) {
		Thread thread = new Thread(() -> {
			await(startTogether);
			new ClaudeTrustStore(config).trust(directory);
		});
		thread.start();
		return thread;
	}

	private void await(CyclicBarrier startTogether) {
		try {
			startTogether.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (BrokenBarrierException e) {
			throw new IllegalStateException(e);
		}
	}
}
