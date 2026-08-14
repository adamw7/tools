package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * once — the MCP server serves calls in parallel — would each build on a document
 * read before the other's write landed, so the later write drops the earlier one's
 * entry and that adoption runs {@code claude} in a directory that was never
 * trusted. Atomicity of the write alone cannot prevent it: both writes are
 * complete, the second is simply built on stale text.
 *
 * <p>That lock reaches every adoption and nothing else. {@code claude} writes this
 * file too and knows nothing of a lock this module invented, so its write is
 * noticed rather than excluded: the configuration is read again immediately before
 * the move, and an update built on a document that has since been replaced is
 * thrown away and built again on the new one. Serialising the document — the slow
 * part — therefore happens outside that window, leaving a read and a move.
 */
public class ClaudeTrustStore {

	private static final String PROJECTS = "projects";
	private static final String TRUST_FLAG = "hasTrustDialogAccepted";

	/** The suffix of the lock file guarding the configuration; see {@link #trust}. */
	private static final String LOCK_SUFFIX = ".adopt-lock";

	/**
	 * Serialises the trust updates of this JVM. A {@link FileLock} is held by the
	 * process rather than the thread, so it excludes a second adoption process but
	 * not a second thread of this one — which
	 * {@link java.nio.channels.OverlappingFileLockException} would answer rather than
	 * make wait. Both guards are needed, and this one is taken first.
	 */
	private static final Object IN_PROCESS_LOCK = new Object();

	/**
	 * How many times the read-modify-write is rebuilt when another writer lands
	 * inside it. Each attempt is a read and a move, so an adoption only exhausts
	 * these against something rewriting the configuration continuously — at which
	 * point saying so beats writing over it.
	 */
	static final int WRITE_ATTEMPTS = 3;

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
	 * process and outside it — for the whole read-modify-write, and detecting the one
	 * writer it cannot exclude: {@code claude} itself.
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
	 * a lock held on the old file would guard an inode nothing reads any more. That
	 * file is left behind on the way out, since a lock file deleted by the process
	 * releasing it is a lock the next one takes on an inode of its own.
	 */
	private boolean trustExclusively(Path directory, Path target) {
		Path lockFile = target.resolveSibling(target.getFileName() + LOCK_SUFFIX);
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE); FileLock exclusive = channel.lock()) {
			return update(directory, WRITE_ATTEMPTS);
		} catch (IOException e) {
			throw new AdoptionException("Could not lock the Claude config for update: " + configFile, e);
		}
	}

	/**
	 * Runs under both locks: every read here decides a write that follows it. The
	 * document the decision was taken on is carried to the write as {@code observed},
	 * so a write built on a document {@code claude} has since replaced is recognised
	 * as stale rather than landing on top of it.
	 */
	private boolean update(Path directory, int attemptsLeft) {
		String observed = readText();
		ObjectNode root = parse(observed);
		ObjectNode project = objectChild(objectChild(root, PROJECTS), key(directory));
		if (project.path(TRUST_FLAG).asBoolean(false)) {
			return false;
		}
		project.put(TRUST_FLAG, true);
		if (writeUnlessChanged(root, observed)) {
			return true;
		}
		return retry(directory, attemptsLeft);
	}

	/**
	 * Builds the update again on what the other writer left, rather than on the
	 * document it replaced. Reading afresh is the whole point: the entry that writer
	 * added is in the document this attempt starts from, so both survive.
	 */
	private boolean retry(Path directory, int attemptsLeft) {
		if (attemptsLeft <= 1) {
			throw new AdoptionException("Could not update the Claude config in " + WRITE_ATTEMPTS
					+ " attempts because something else kept rewriting it, and overwriting it would discard"
					+ " whatever that wrote. Close any running claude session and adopt again: " + configFile);
		}
		return update(directory, attemptsLeft - 1);
	}

	/** The {@code projects} key {@code claude} records a directory under. */
	private static String key(Path directory) {
		return directory.toAbsolutePath().normalize().toString();
	}

	/**
	 * A field the document does not carry is created, and one that is already an
	 * object is used as-is. Anything else is refused rather than replaced: writing
	 * over it would discard whatever it held, and this is Claude Code's own per-user
	 * state rather than a file the adoption owns.
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

	/**
	 * The configuration as text: the one read an update is built from, and the one it
	 * is checked against just before the move. A file that is not there reads as the
	 * empty string, which a file appearing in the meantime no longer equals — so a
	 * {@code claude} that created the configuration inside the window is caught by
	 * the same comparison as one that rewrote it. Package-visible so a test can put a
	 * competing writer in that window.
	 */
	String readText() {
		if (!Files.isRegularFile(configFile)) {
			return "";
		}
		try {
			return Files.readString(configFile);
		} catch (NoSuchFileException e) {
			return "";
		} catch (IOException e) {
			throw new AdoptionException("Could not read Claude config: " + configFile, e);
		}
	}

	/**
	 * The document is parsed from the text that was read rather than from the file
	 * again, so what the update is built on and what it is compared against before
	 * the move can never be two different reads of a file that changed between them.
	 */
	private ObjectNode parse(String text) {
		try {
			return asObjectOrFresh(mapper.readTree(text));
		} catch (JsonProcessingException e) {
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
	 * per-user state on the write completing, and this is a live file an interactive
	 * session may be writing at the same moment.
	 *
	 * @param observed the configuration the document was built on
	 * @return whether it landed, or {@code false} when the configuration changed
	 *         under the update and it has to be built again
	 */
	private boolean writeUnlessChanged(ObjectNode root, String observed) {
		Path target = configFile.toAbsolutePath();
		try {
			return replaceUnlessChanged(root, temporaryBeside(target), target, observed);
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
	private boolean replaceUnlessChanged(ObjectNode root, Path temporary, Path target, String observed)
			throws IOException {
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
			return moveUnlessChanged(temporary, target, observed);
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(temporary);
			throw e;
		}
	}

	/**
	 * Reads the configuration one last time and moves the new document over it only
	 * if it is still the one the update was built on. This is what the lock cannot do
	 * for {@code claude}: it takes no lock of ours, so the only way to keep its write
	 * is to notice it. The check sits after the serialisation, so the span between
	 * deciding what to write and writing it is a read and a move rather than the whole
	 * read-modify-write. The temporary file is removed on the stale path too, since it
	 * carries a document that is never going to land.
	 *
	 * @return whether the document was moved over the configuration
	 */
	private boolean moveUnlessChanged(Path temporary, Path target, String observed) throws IOException {
		if (!readText().equals(observed)) {
			Files.deleteIfExists(temporary);
			return false;
		}
		replace(temporary, target);
		return true;
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
