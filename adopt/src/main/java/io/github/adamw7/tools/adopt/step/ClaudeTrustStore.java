package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Records a directory as trusted in Claude Code's per-user configuration
 * ({@code ~/.claude.json}) by setting {@code hasTrustDialogAccepted} on the
 * directory's {@code projects} entry, so a headless {@code claude} run started
 * in that directory is not blocked by the interactive "Do you trust the files
 * in this folder?" prompt.
 *
 * <p>The whole configuration document is read, updated, and written back through
 * Jackson's tree model, so every unrelated field the file already carries is
 * preserved. A missing configuration file is created, and a directory that is
 * already trusted is left untouched. The write itself goes through a temporary
 * file and a move, so the configuration is never seen half-written.
 *
 * <p>The read and the write are one exclusive section, because they are a
 * read-modify-write of a file this process does not own. Two adoptions running at
 * once — the MCP server serves calls in parallel — each read the document, each
 * add their own directory, and each write the whole of it back, so the later write
 * dropped the earlier one's entry. The adoption that lost it went on to run
 * {@code claude} in a directory that was never trusted, where it blocked on the
 * interactive prompt until the command timeout killed it. Atomicity of the write
 * alone cannot prevent that: the two writes are each complete, and the second is
 * simply built on a document read before the first landed.
 */
public class ClaudeTrustStore {

	private static final String PROJECTS = "projects";
	private static final String TRUST_FLAG = "hasTrustDialogAccepted";

	/** The suffix of the lock file guarding the configuration; see {@link #trust}. */
	private static final String LOCK_SUFFIX = ".adopt-lock";

	/**
	 * Serialises the trust updates of this JVM. A {@link FileLock} is held by the
	 * process rather than the thread, so it excludes a concurrent {@code claude} but
	 * not a second thread of this adoption — which
	 * {@link java.nio.channels.OverlappingFileLockException} would answer rather than
	 * make wait. Both guards are needed, and this one is taken first.
	 */
	private static final Object IN_PROCESS_LOCK = new Object();

	private final Path configFile;
	private final ObjectMapper mapper = new ObjectMapper();

	public ClaudeTrustStore() {
		this(defaultConfigFile());
	}

	public ClaudeTrustStore(Path configFile) {
		this.configFile = configFile;
	}

	private static Path defaultConfigFile() {
		return Path.of(System.getProperty("user.home"), ".claude.json");
	}

	/**
	 * Records the directory as trusted, excluding every other adoption — in this
	 * process and outside it — for the whole read-modify-write.
	 *
	 * @return {@code true} when the directory was newly trusted, {@code false}
	 *         when it was already trusted and the file was left unchanged.
	 */
	public boolean trust(Path directory) {
		Path target = configFile.toAbsolutePath();
		createConfigDirectory(target);
		synchronized (IN_PROCESS_LOCK) {
			return trustExclusively(directory, target);
		}
	}

	/**
	 * The lock is taken on a file beside the configuration rather than on the
	 * configuration itself, because {@link #replace} moves a new file over that path:
	 * a lock held on the old file would guard an inode nothing reads any more.
	 */
	private boolean trustExclusively(Path directory, Path target) {
		Path lockFile = target.resolveSibling(target.getFileName() + LOCK_SUFFIX);
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE); FileLock exclusive = channel.lock()) {
			return update(directory);
		} catch (IOException e) {
			throw new AdoptionException("Could not lock the Claude config for update: " + configFile, e);
		}
	}

	/** Runs under both locks: every read here decides a write that follows it. */
	private boolean update(Path directory) {
		String key = directory.toAbsolutePath().normalize().toString();
		ObjectNode root = readRoot();
		ObjectNode project = objectChild(objectChild(root, PROJECTS), key);
		if (project.path(TRUST_FLAG).asBoolean(false)) {
			return false;
		}
		project.put(TRUST_FLAG, true);
		write(root);
		return true;
	}

	/**
	 * A field the document does not carry is created, and one that is already an
	 * object is used as-is. A field carrying anything else is refused rather than
	 * replaced, for the same reason {@link #asObjectOrFresh} refuses a non-object
	 * document: writing over it would discard whatever it held, and this is Claude
	 * Code's own per-user state rather than a file the adoption owns.
	 */
	private ObjectNode objectChild(ObjectNode parent, String name) {
		JsonNode existing = parent.get(name);
		if (existing == null || existing.isNull()) {
			return parent.putObject(name);
		}
		if (existing instanceof ObjectNode object) {
			return object;
		}
		throw new AdoptionException("Refusing to overwrite '" + name + "' in the Claude config;"
				+ " expected a JSON object but found " + existing.getNodeType() + ": " + configFile);
	}

	private ObjectNode readRoot() {
		if (!Files.isRegularFile(configFile)) {
			return mapper.createObjectNode();
		}
		return parse();
	}

	private ObjectNode parse() {
		try {
			return asObjectOrFresh(mapper.readTree(configFile.toFile()));
		} catch (IOException e) {
			throw new AdoptionException("Could not read Claude config: " + configFile, e);
		}
	}

	/**
	 * An empty file (a {@code null} or missing tree) starts fresh, and an object is
	 * used as-is. Any other valid JSON — a top-level array or scalar — is refused
	 * rather than silently replaced, because overwriting it would discard whatever
	 * the file already held.
	 */
	private ObjectNode asObjectOrFresh(JsonNode root) {
		if (root == null || root.isMissingNode()) {
			return mapper.createObjectNode();
		}
		if (root instanceof ObjectNode object) {
			return object;
		}
		throw new AdoptionException("Refusing to overwrite Claude config; expected a JSON object but found "
				+ root.getNodeType() + ": " + configFile);
	}

	/**
	 * Writes the document to a sibling temporary file and moves it over the
	 * configuration, so a reader only ever sees the old file or the new one.
	 * Truncating {@code ~/.claude.json} in place risked the whole of Claude Code's
	 * per-user state — its project list, history, and onboarding — on the write
	 * completing: a crash part-way through left a truncated document behind, and
	 * this is a live file that an interactive session, or the {@code claude init}
	 * the adoption is about to run, may be writing at the same moment.
	 */
	private void write(ObjectNode root) {
		Path target = configFile.toAbsolutePath();
		try {
			replaceWith(root, temporaryBeside(target), target);
		} catch (IOException e) {
			throw new AdoptionException("Could not write Claude config: " + configFile, e);
		}
	}

	/** Created before the lock file is opened beside it, and so before anything is read. */
	private void createConfigDirectory(Path target) {
		try {
			Files.createDirectories(target.getParent());
		} catch (IOException e) {
			throw new AdoptionException("Could not create the Claude config directory: " + target.getParent(), e);
		}
	}

	/** A sibling, so the move stays within one filesystem and can be atomic. */
	private Path temporaryBeside(Path target) throws IOException {
		return Files.createTempFile(target.getParent(), ".claude-adopt-", ".json");
	}

	/**
	 * The temporary file is removed on every path the move does not take it, so a
	 * write that fails leaves the configuration directory as it found it rather than
	 * scattering half-written documents beside the file {@code claude} reads.
	 */
	private void replaceWith(ObjectNode root, Path temporary, Path target) throws IOException {
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
			replace(temporary, target);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(temporary);
			throw e;
		}
	}

	/**
	 * Falls back to a plain replacing move where the filesystem cannot do an atomic
	 * one, which still beats writing the configuration in place: the document is
	 * complete on disk before anything of the old one is touched.
	 */
	private void replace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
