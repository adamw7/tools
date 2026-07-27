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
 * untouched, and one whose {@code build} already uses the plugin for other rules
 * has that declaration augmented in place, so the project keeps a single enforcer
 * entry. {@link PomDocument} does the reading, splicing, and verbatim writing, and
 * the rule version comes from {@link EnforcerRuleVersion} and must be a release.
 */
public class PomEnforcerInstaller {

	static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String ENFORCER_VERSION = "3.6.3";
	static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	static final String RULE_GROUP_ID = "io.github.adamw7";
	static final String CLAUDE_MD_FILE = "${project.basedir}/CLAUDE.md";

	/** The one place a plugin both runs from and is added to; see {@link #enforcerPluginOfTheBuild}. */
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
		enforcerPluginOfTheBuild(pom).ifPresentOrElse(
				plugin -> augment(pom, plugin),
				() -> pom.insertUnder(pom.root(), BUILD_PLUGINS, plugin()));
		pom.write();
		return true;
	}

	/**
	 * Asks every plugin the POM <em>binds</em>, not only its {@code build/plugins},
	 * because a project that already runs the rule may well wire it behind an opt-in
	 * profile. Looking only at the build would report such a POM as unguarded and
	 * wire in a second, always-on copy of a rule the project already runs on its own
	 * terms.
	 *
	 * <p>What it does not ask is {@code pluginManagement}, which binds nothing: a
	 * rule declared only there never runs, so treating it as a guard left the project
	 * with none and the pull request still claiming one — the same silent outcome
	 * {@link #enforcerPluginOfTheBuild} refuses to produce from the other direction,
	 * since {@link VerifyStep}'s {@code mvn -N validate} passes either way.
	 */
	private boolean declaresClaudeRule(PomDocument pom) {
		return pom.boundPlugins().stream().anyMatch(this::declaresRuleDependency);
	}

	private boolean declaresRuleDependency(Element plugin) {
		return PomDocument.child(plugin, "dependencies").stream()
				.flatMap(dependencies -> PomDocument.children(dependencies, "dependency").stream())
				.anyMatch(dependency -> PomDocument.hasArtifactId(dependency, RULE_ARTIFACT_ID));
	}

	/**
	 * The {@code maven-enforcer-plugin} of the POM's own {@code build}, and only that
	 * one. Where the rule is <em>looked for</em> is every plugin the POM binds — see
	 * {@link #declaresClaudeRule} — but where it is <em>added</em> cannot be: an
	 * execution spliced into {@code pluginManagement} only configures a plugin the
	 * build never runs, and one spliced into a profile runs only when that profile is
	 * activated. Neither enforces anything, and neither shows up as a failure —
	 * {@link VerifyStep}'s {@code mvn -N validate} would pass without the rule ever
	 * executing and the adoption would advertise a guard the project does not have. A
	 * POM whose only enforcer sits somewhere else therefore gets its own declaration
	 * in {@code build/plugins}.
	 */
	private Optional<Element> enforcerPluginOfTheBuild(PomDocument pom) {
		return pom.at(BUILD_PLUGINS).stream()
				.flatMap(plugins -> PomDocument.children(plugins, "plugin").stream())
				.filter(plugin -> PomDocument.hasArtifactId(plugin, ENFORCER_ARTIFACT_ID))
				.findFirst();
	}

	/**
	 * Adds the rule dependency and the execution to the {@code maven-enforcer-plugin}
	 * the build already declares — reusing its {@code dependencies} and
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
