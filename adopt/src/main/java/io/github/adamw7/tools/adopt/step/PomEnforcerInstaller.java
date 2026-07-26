package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
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
 * <p>{@link PomDocument} does the reading, splicing, and verbatim writing, so the
 * existing document is preserved exactly and only the newly added markup appears in
 * the adoption commit. The rule version comes from {@link EnforcerRuleVersion} and
 * must be a release.
 */
public class PomEnforcerInstaller {

	static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String ENFORCER_VERSION = "3.6.3";
	static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	static final String RULE_GROUP_ID = "io.github.adamw7";
	static final String CLAUDE_MD_FILE = "${project.basedir}/CLAUDE.md";

	/** Where a plugin belongs in a POM that does not declare the enforcer yet. */
	private static final List<String> BUILD_PLUGINS = List.of("build", "plugins");

	/**
	 * The execution that runs the rule, bound to {@code validate} and not inherited
	 * because {@code CLAUDE.md} lives only at the repository root.
	 */
	private static final String EXECUTION = """
			<execution>
			  <id>enforce-claude-md</id>
			  <phase>validate</phase>
			  <inherited>false</inherited>
			  <goals>
			    <goal>enforce</goal>
			  </goals>
			  <configuration>
			    <rules>
			      <claudeMdFormat>
			        <claudeMdFile>%s</claudeMdFile>
			      </claudeMdFormat>
			    </rules>
			  </configuration>
			</execution>""".formatted(CLAUDE_MD_FILE);

	private final Supplier<String> ruleVersion;

	public PomEnforcerInstaller() {
		this.ruleVersion = EnforcerRuleVersion::release;
	}

	/**
	 * Pins an explicitly supplied version instead of the running build's. It is
	 * checked here, before anything is cloned, because a version named on the command
	 * line is worth rejecting immediately rather than at the one step that edits a
	 * POM; the reason a snapshot cannot be wired in does not change for being asked
	 * for on purpose.
	 *
	 * @param ruleVersion a released {@code claude-code-enforcer} version
	 */
	public PomEnforcerInstaller(String ruleVersion) {
		String release = EnforcerRuleVersion.requireRelease(ruleVersion);
		this.ruleVersion = () -> release;
	}

	/**
	 * @return {@code true} when the rule was wired in, {@code false} when the POM
	 *         already declared the {@code claude-code-enforcer} rule and was left
	 *         unchanged.
	 */
	public boolean install(Path pomFile) {
		PomDocument pom = PomDocument.read(pomFile);
		if (declaresClaudeRule(pom)) {
			return false;
		}
		enforcerPlugin(pom).ifPresentOrElse(
				plugin -> augment(pom, plugin),
				() -> pom.insertUnder(pom.root(), BUILD_PLUGINS, plugin()));
		pom.write();
		return true;
	}

	/**
	 * Asks the whole POM rather than only its {@code build/plugins}, because a
	 * project that already runs the rule may well wire it somewhere else — behind an
	 * opt-in profile, or in {@code pluginManagement}. Looking only at the build would
	 * report such a POM as unguarded and wire in a second, duplicate declaration.
	 */
	private boolean declaresClaudeRule(PomDocument pom) {
		return pom.plugins().stream().anyMatch(this::declaresRuleDependency);
	}

	private boolean declaresRuleDependency(Element plugin) {
		return PomDocument.child(plugin, "dependencies").stream()
				.flatMap(dependencies -> PomDocument.children(dependencies, "dependency").stream())
				.anyMatch(dependency -> PomDocument.hasArtifactId(dependency, RULE_ARTIFACT_ID));
	}

	private Optional<Element> enforcerPlugin(PomDocument pom) {
		return pom.plugins().stream()
				.filter(plugin -> PomDocument.hasArtifactId(plugin, ENFORCER_ARTIFACT_ID))
				.findFirst();
	}

	/**
	 * Adds the rule dependency and the execution to a {@code maven-enforcer-plugin}
	 * the project already declares — reusing its {@code dependencies} and
	 * {@code executions} when it has them — so the project keeps a single enforcer
	 * plugin entry and the rules it already ran keep running.
	 */
	private void augment(PomDocument pom, Element plugin) {
		pom.insertUnder(plugin, List.of("dependencies"), ruleDependency());
		pom.insertUnder(plugin, List.of("executions"), EXECUTION);
	}

	/** A freshly declared enforcer plugin: pinned to a version, carrying the rule and its execution. */
	private String plugin() {
		return PomDocument.wrapped("plugin", String.join("\n",
				element("groupId", ENFORCER_GROUP_ID),
				element("artifactId", ENFORCER_ARTIFACT_ID),
				element("version", ENFORCER_VERSION),
				PomDocument.wrapped("dependencies", ruleDependency()),
				PomDocument.wrapped("executions", EXECUTION)));
	}

	private String ruleDependency() {
		return PomDocument.wrapped("dependency", String.join("\n",
				element("groupId", RULE_GROUP_ID),
				element("artifactId", RULE_ARTIFACT_ID),
				element("version", ruleVersion.get())));
	}

	private String element(String name, String text) {
		return "<" + name + ">" + text + "</" + name + ">";
	}
}
