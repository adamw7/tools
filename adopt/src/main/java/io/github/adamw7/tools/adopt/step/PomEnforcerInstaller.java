package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import org.w3c.dom.Element;

/**
 * Adds the {@code claude-code-enforcer} to a Maven project's {@code pom.xml} by
 * wiring the {@code maven-enforcer-plugin} with the {@code claudeMdFormat} rule,
 * so the adopted repository fails its build if the freshly generated
 * {@code CLAUDE.md} is missing or malformed.
 *
 * <p>The install is idempotent: a POM that already wires the rule is left
 * untouched. A POM that already uses the {@code maven-enforcer-plugin} for other
 * rules is augmented in place rather than skipped, so the rule is still wired in.
 *
 * <p>{@link PomDocument} does the reading, appending, and verbatim writing, so
 * the existing document is preserved exactly and only the newly added elements
 * appear in the adoption commit. The rule version comes from
 * {@link EnforcerRuleVersion} and must be a release.
 */
public class PomEnforcerInstaller {

	static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String ENFORCER_VERSION = "3.6.3";
	static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	static final String RULE_GROUP_ID = "io.github.adamw7";
	static final String CLAUDE_MD_FILE = "${project.basedir}/CLAUDE.md";

	private final Supplier<String> ruleVersion;

	public PomEnforcerInstaller() {
		this.ruleVersion = EnforcerRuleVersion::release;
	}

	public PomEnforcerInstaller(String ruleVersion) {
		this.ruleVersion = () -> ruleVersion;
	}

	/**
	 * @return {@code true} when the rule was wired in, {@code false} when the POM
	 *         already declared the {@code claude-code-enforcer} rule and was left
	 *         unchanged.
	 */
	public boolean install(Path pomFile) {
		PomDocument pom = PomDocument.read(pomFile);
		Element plugins = pom.pluginsElement();
		if (declaresClaudeRule(plugins)) {
			return false;
		}
		wireEnforcer(pom, plugins);
		pom.write();
		return true;
	}

	private boolean declaresClaudeRule(Element plugins) {
		return PomDocument.children(plugins, "plugin").stream().anyMatch(this::declaresRuleDependency);
	}

	private boolean declaresRuleDependency(Element plugin) {
		return PomDocument.child(plugin, "dependencies").stream()
				.flatMap(dependencies -> PomDocument.children(dependencies, "dependency").stream())
				.anyMatch(dependency -> PomDocument.hasArtifactId(dependency, RULE_ARTIFACT_ID));
	}

	/**
	 * Adds the rule dependency and its {@code enforce} execution to the POM's
	 * {@code maven-enforcer-plugin}, reusing an existing plugin declaration when
	 * one is present so the project keeps a single enforcer plugin entry.
	 */
	private void wireEnforcer(PomDocument pom, Element plugins) {
		Element plugin = findEnforcerPlugin(plugins).orElseGet(() -> createEnforcerPlugin(pom, plugins));
		ruleDependency(pom, pom.childOrCreate(plugin, "dependencies"));
		enforceExecution(pom, pom.childOrCreate(plugin, "executions"));
	}

	private Optional<Element> findEnforcerPlugin(Element plugins) {
		return PomDocument.children(plugins, "plugin").stream()
				.filter(plugin -> PomDocument.hasArtifactId(plugin, ENFORCER_ARTIFACT_ID))
				.findFirst();
	}

	private Element createEnforcerPlugin(PomDocument pom, Element plugins) {
		Element plugin = pom.appendElement(plugins, "plugin");
		pom.appendText(plugin, "groupId", ENFORCER_GROUP_ID);
		pom.appendText(plugin, "artifactId", ENFORCER_ARTIFACT_ID);
		pom.appendText(plugin, "version", ENFORCER_VERSION);
		return plugin;
	}

	private void ruleDependency(PomDocument pom, Element dependencies) {
		Element dependency = pom.appendElement(dependencies, "dependency");
		pom.appendText(dependency, "groupId", RULE_GROUP_ID);
		pom.appendText(dependency, "artifactId", RULE_ARTIFACT_ID);
		pom.appendText(dependency, "version", ruleVersion.get());
	}

	private void enforceExecution(PomDocument pom, Element executions) {
		Element execution = pom.appendElement(executions, "execution");
		pom.appendText(execution, "id", "enforce-claude-md");
		pom.appendText(execution, "phase", "validate");
		pom.appendText(execution, "inherited", "false");
		Element goals = pom.appendElement(execution, "goals");
		pom.appendText(goals, "goal", "enforce");
		claudeMdConfiguration(pom, execution);
	}

	private void claudeMdConfiguration(PomDocument pom, Element execution) {
		Element configuration = pom.appendElement(execution, "configuration");
		Element rules = pom.appendElement(configuration, "rules");
		Element claudeMdFormat = pom.appendElement(rules, "claudeMdFormat");
		pom.appendText(claudeMdFormat, "claudeMdFile", CLAUDE_MD_FILE);
	}
}
