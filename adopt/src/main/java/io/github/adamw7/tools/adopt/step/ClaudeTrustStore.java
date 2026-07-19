package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * already trusted is left untouched.
 */
public class ClaudeTrustStore {

	static final String PROJECTS = "projects";
	static final String TRUST_FLAG = "hasTrustDialogAccepted";

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

	private void write(ObjectNode root) {
		try {
			Files.createDirectories(configFile.toAbsolutePath().getParent());
			mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), root);
		} catch (IOException e) {
			throw new AdoptionException("Could not write Claude config: " + configFile, e);
		}
	}
}
