package io.github.adamw7.tools.enforcer.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * The repository's own root {@code pom.xml}, read as the configuration under test
 * rather than as a build file. Two facts are taken from it: the plugin versions a
 * fixture build must use, so an end-to-end test exercises the same
 * maven-enforcer-plugin the repository does rather than a version of its own, and
 * the set of rules the {@code claude-md-enforce} profile actually wires — the
 * catalogue a contributor's build runs, which is what
 * {@link RepositoryEnforcementIT} holds against the rules this module ships.
 */
final class RepositoryPom {

	private static final String ENFORCE_PROFILE = "claude-md-enforce";

	private final Document pom;
	private final Path file;

	RepositoryPom(Path repositoryRoot) {
		this.file = repositoryRoot.resolve("pom.xml");
		this.pom = parse(file);
	}

	/**
	 * The version pinned for {@code artifactId} under {@code pluginManagement}, where
	 * this repository keeps every plugin version.
	 */
	String pluginVersion(String artifactId) {
		for (Element plugin : pom.select("pluginManagement > plugins > plugin")) {
			if (artifactId.equals(textOf(plugin, "artifactId"))) {
				return required(textOf(plugin, "version"), artifactId + " has no version in " + file);
			}
		}
		throw new IllegalStateException(artifactId + " is not managed in " + file);
	}

	/**
	 * The rule names the doc check is wired with, in pom order. Each is the name of a
	 * configuration element, which is also the {@code @Named} name of the rule class
	 * behind it — the whole point of the comparison this feeds.
	 */
	Set<String> wiredRuleNames() {
		Set<String> names = new LinkedHashSet<>();
		for (Element rule : enforceProfileRules().children()) {
			names.add(rule.tagName());
		}
		return names;
	}

	/**
	 * Scoped to the {@code claude-md-enforce} profile on purpose: the root pom
	 * carries other {@code <rules>} blocks — the standing enforcer checks, the
	 * coverage limits — and picking the first one found would compare the doc-check
	 * catalogue against something else entirely.
	 */
	private Element enforceProfileRules() {
		for (Element profile : pom.select("profiles > profile")) {
			if (ENFORCE_PROFILE.equals(textOf(profile, "id"))) {
				Elements rules = profile.select("rules");
				return required(rules.first(), "The " + ENFORCE_PROFILE + " profile has no rules in " + file);
			}
		}
		throw new IllegalStateException("There is no " + ENFORCE_PROFILE + " profile in " + file);
	}

	/**
	 * The text of a <em>direct</em> child element, or null when there is none. Direct
	 * rather than descendant, so a plugin's own {@code artifactId} is not confused
	 * with one nested in its {@code dependencies}.
	 */
	private String textOf(Element parent, String tagName) {
		for (Element child : parent.children()) {
			if (child.tagName().equals(tagName)) {
				return child.text().strip();
			}
		}
		return null;
	}

	private <T> T required(T value, String message) {
		if (value == null) {
			throw new IllegalStateException(message);
		}
		return value;
	}

	/**
	 * Parsed with the XML parser rather than the HTML one, which lower-cases tag
	 * names — a rule element such as {@code claudeMdFormat} would come back as
	 * {@code claudemdformat} and match no rule.
	 */
	private static Document parse(Path file) {
		try {
			return Jsoup.parse(file.toFile(), StandardCharsets.UTF_8.name(), "", Parser.xmlParser());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read " + file, e);
		}
	}
}
