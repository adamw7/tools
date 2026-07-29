package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
 */
public class ClaudeTrustStore {

	private static final String PROJECTS = "projects";
	private static final String TRUST_FLAG = "hasTrustDialogAccepted";

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
	 * @return {@code true} when the directory was newly trusted, {@code false}
	 *         when it was already trusted and the file was left unchanged.
	 */
	public boolean trust(Path directory) {
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

	private ObjectNode objectChild(ObjectNode parent, String name) {
		JsonNode existing = parent.get(name);
		return existing instanceof ObjectNode object ? object : parent.putObject(name);
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
			Files.createDirectories(target.getParent());
			replaceWith(root, temporaryBeside(target), target);
		} catch (IOException e) {
			throw new AdoptionException("Could not write Claude config: " + configFile, e);
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
