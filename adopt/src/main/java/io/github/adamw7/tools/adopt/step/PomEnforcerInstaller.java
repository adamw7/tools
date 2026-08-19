package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.nodes.Element;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * Adds the {@code claude-code-enforcer} to a Maven project's {@code pom.xml} by
 * wiring the {@code maven-enforcer-plugin} with the {@code claudeMdFormat} rule,
 * so the adopted repository fails its build if the freshly generated
 * {@code CLAUDE.md} is missing or malformed.
 *
 * <p>The install is idempotent: a POM that already wires the rule is left
 * untouched, and one whose {@code build} already uses the plugin for other rules
 * has that declaration augmented in place, so the project keeps a single enforcer
 * entry — unless that declaration is pinned too far back to run the rule at all,
 * which is refused rather than wired in (see {@link #requireRunnableEnforcer}).
 * {@link PomDocument} does the reading, splicing, and verbatim writing, and the
 * rule version comes from {@link EnforcerRuleVersion} and must be a release.
 */
public class PomEnforcerInstaller {

	private static final String ENFORCER_GROUP_ID = "org.apache.maven.plugins";
	static final String ENFORCER_ARTIFACT_ID = "maven-enforcer-plugin";
	static final String ENFORCER_VERSION = "3.6.3";

	/**
	 * The oldest {@value #ENFORCER_ARTIFACT_ID} that can run the rule wired in here.
	 * {@value #CLAUDE_MD_RULE} is looked up by the component name it is published
	 * under, which the plugin only does from this version; an older one fails the
	 * build complaining it cannot find a rule the POM plainly declares. A declaration
	 * this installer writes itself pins {@value #ENFORCER_VERSION}, so only a version
	 * the project pinned — in its {@code build} or in the {@code pluginManagement}
	 * that declaration resolves through — can be too old; see
	 * {@link #requireRunnableEnforcer}.
	 */
	static final String MINIMUM_ENFORCER_VERSION = "3.1.0";
	private static final String RULE_ARTIFACT_ID = "tools.claude-code-enforcer";
	private static final String RULE_GROUP_ID = "io.github.adamw7";
	private static final String CLAUDE_MD_FILE = "${project.basedir}/CLAUDE.md";

	/**
	 * The rule the adoption wires in, named once so the declaration it writes and the
	 * declaration it looks for cannot spell it differently.
	 */
	static final String CLAUDE_MD_RULE = "claudeMdFormat";

	/** The element a maven-enforcer rule is configured under; see {@link #runsClaudeMdRule}. */
	private static final String RULES = "rules";

	/** The one place a plugin both runs from and is added to; see {@link #enforcerPluginOfTheBuild}. */
	private static final List<String> BUILD_PLUGINS = List.of("build", "plugins");

	/**
	 * Where a POM pins the version of a plugin its {@code build} declares without one
	 * — the idiomatic Maven arrangement, and so the version
	 * {@link #requireRunnableEnforcer} has to read when the declaration itself names
	 * none.
	 */
	private static final List<String> MANAGED_PLUGINS = List.of("build", "pluginManagement", "plugins");

	/** Where the refused version was read from, named so the operator knows which declaration to raise. */
	private static final String IN_BUILD = "build/plugins";
	private static final String IN_PLUGIN_MANAGEMENT = "build/pluginManagement";

	/** The composite rule, which checks the whole configuration from the project directory. */
	static final String PROJECT_RULE = GuardRules.PROJECT.ruleName();

	/** The project directory the composite resolves every conventional path against. */
	private static final String PROJECT_DIR = "${project.basedir}";

	/**
	 * The execution that runs the guard, bound to {@code validate} and not inherited
	 * because the documents live only at the repository root.
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
			%s
			    </rules>
			  </configuration>
			</execution>""";

	private final Supplier<String> ruleVersion;
	private final GuardRules rules;

	public PomEnforcerInstaller() {
		this(EnforcerRuleVersion::release, GuardRules.PROJECT);
	}

	/**
	 * Pins an explicitly supplied version instead of the running build's, checked here
	 * — before anything is cloned — rather than at the one step that edits a POM.
	 *
	 * @param ruleVersion a released {@code claude-code-enforcer} version
	 */
	public PomEnforcerInstaller(String ruleVersion) {
		this(released(ruleVersion), GuardRules.PROJECT);
	}

	private PomEnforcerInstaller(Supplier<String> ruleVersion, GuardRules rules) {
		this.ruleVersion = ruleVersion;
		this.rules = rules;
	}

	private static Supplier<String> released(String ruleVersion) {
		String release = EnforcerRuleVersion.requireRelease(ruleVersion);
		return () -> release;
	}

	/**
	 * The installer the guard options describe: the version they pin, or the running
	 * build's when they pin none, wiring the rule set they name. Naming the choice
	 * here keeps callers from spelling out which of the constructors an empty
	 * {@link Optional} means.
	 */
	static PomEnforcerInstaller from(GuardOptions guard) {
		Supplier<String> version = guard.pinnedRuleVersion().map(PomEnforcerInstaller::released)
				.orElse(EnforcerRuleVersion::release);
		return new PomEnforcerInstaller(version, guard.rules());
	}


	/**
	 * @return {@code true} when the rule was wired in, {@code false} when the POM
	 *         already runs the {@code claudeMdFormat} rule and was left unchanged.
	 * @throws io.github.adamw7.tools.adopt.AdoptionException when the project's own
	 *         {@value #ENFORCER_ARTIFACT_ID} is pinned too far back to run the rule
	 */
	public boolean install(Path pomFile) {
		return install(pomFile, List.of());
	}

	/**
	 * @param requiredSections the {@code CLAUDE.md} headings the guard is to demand,
	 *                         which are the headings the conformer has just reshaped
	 *                         the document to. Written into the POM rather than left
	 *                         to the rule's defaults, so a project whose document is
	 *                         not a Java project's is held to its own headings instead
	 *                         of to this repository's.
	 * @return {@code true} when the guard was wired in, {@code false} when the POM
	 *         already runs it and was left unchanged
	 */
	public boolean install(Path pomFile, List<String> requiredSections) {
		PomDocument pom = PomDocument.read(pomFile);
		if (alreadyRunsTheRule(pom)) {
			return false;
		}
		String execution = EXECUTION.formatted(ruleConfiguration(requiredSections));
		enforcerPluginOfTheBuild(pom).ifPresentOrElse(
				plugin -> augment(pom, plugin, execution),
				() -> pom.insertUnder(pom.root(), BUILD_PLUGINS, plugin(execution)));
		pom.write();
		return true;
	}

	/**
	 * The rule element the execution configures, which is the whole difference between
	 * the two rule sets: one document checked, or the whole configuration. Both are
	 * given the section list, because both hold {@code CLAUDE.md} to it.
	 */
	private String ruleConfiguration(List<String> requiredSections) {
		return rules == GuardRules.PROJECT
				? projectRule(requiredSections)
				: claudeMdRule(requiredSections);
	}

	private String claudeMdRule(List<String> requiredSections) {
		return PomDocument.wrapped(CLAUDE_MD_RULE,
				element("claudeMdFile", CLAUDE_MD_FILE) + sectionsElement(requiredSections));
	}

	private String projectRule(List<String> requiredSections) {
		return PomDocument.wrapped(PROJECT_RULE,
				element("projectDir", PROJECT_DIR) + claudeMdSectionsElement(requiredSections));
	}

	/**
	 * Empty when the guard demands no particular section, which leaves the rule's own
	 * defaults in place rather than writing an empty list that would demand nothing at
	 * all. A build system whose guard asks only that the document exist is the case:
	 * it has no sections to name, and it is not wiring this rule anyway.
	 */
	private String sectionsElement(List<String> requiredSections) {
		return sections(requiredSections, "requiredSections", "requiredSection");
	}

	private String claudeMdSectionsElement(List<String> requiredSections) {
		return sections(requiredSections, "claudeMdSections", "claudeMdSection");
	}

	private String sections(List<String> requiredSections, String wrapper, String item) {
		if (requiredSections.isEmpty()) {
			return "";
		}
		return PomDocument.wrapped(wrapper, requiredSections.stream()
				.map(section -> element(item, section))
				.collect(Collectors.joining("\n")));
	}

	/**
	 * Asks the plugins of the POM's own {@code build} — the one place a rule runs on
	 * every build, and the very place {@link #enforcerPluginOfTheBuild} would add one,
	 * so what is inspected and what is edited cannot disagree.
	 *
	 * <p>A rule declared anywhere else is not one an ordinary build runs, so it is not
	 * a guard this installer may stand down for: {@code pluginManagement} binds
	 * nothing, a {@code profile} binds only while activated, and a {@code reporting}
	 * plugin does not run in the lifecycle. Counting any of them left the project with
	 * no guard on the build it actually runs, and nothing downstream said so —
	 * {@link VerifyStep}'s {@code mvn -N validate} passes precisely because the rule
	 * never ran. A project that gated the rule behind a profile therefore ends up
	 * running it on every build too, which is the direction to err in: an extra run is
	 * visible and removable, a guard nobody runs reads as adopted and is not.
	 */
	private boolean alreadyRunsTheRule(PomDocument pom) {
		return buildPlugins(pom).anyMatch(this::runsClaudeMdRule);
	}

	/**
	 * Whether the plugin actually configures the {@code claudeMdFormat} rule, rather
	 * than merely carrying the artifact that supplies it. {@value #RULE_ARTIFACT_ID}
	 * ships a rule per document and per piece of agent configuration, so a project
	 * that wired in {@code noSecrets} has the dependency and no {@code CLAUDE.md}
	 * guard at all — and reading the dependency as the guard left that POM untouched
	 * while {@link VerifyStep} passed on the rule the project already had.
	 *
	 * <p>The rule is looked for at any depth below the plugin, because it is
	 * configured either on an execution or on the plugin itself, and only directly
	 * under a {@code rules} element, so a project whose own configuration happens to
	 * name an element the same way elsewhere is not mistaken for one running it.
	 */
	private boolean runsClaudeMdRule(Element plugin) {
		return PomDocument.selfAndDescendants(plugin).stream().anyMatch(this::isGuardRule);
	}

	/**
	 * Either guard counts as one already wired. A repository adopted before the
	 * composite existed runs {@code claudeMdFormat}, and re-adopting it must not
	 * splice a second execution in beside the guard it already has — the run's job is
	 * to leave a repository guarded, and it is.
	 */
	private boolean isGuardRule(Element element) {
		Element parent = element.parent();
		if (parent == null || !RULES.equals(PomDocument.localName(parent))) {
			return false;
		}
		String name = PomDocument.localName(element);
		return CLAUDE_MD_RULE.equals(name) || PROJECT_RULE.equals(name);
	}

	private boolean declaresRuleDependency(Element plugin) {
		return PomDocument.child(plugin, "dependencies").stream()
				.flatMap(dependencies -> PomDocument.children(dependencies, "dependency").stream())
				.anyMatch(dependency -> PomDocument.hasArtifactId(dependency, RULE_ARTIFACT_ID));
	}

	/**
	 * The {@code maven-enforcer-plugin} of the POM's own {@code build}, and only that
	 * one: an execution spliced into {@code pluginManagement} configures a plugin the
	 * build never runs, and one spliced into a profile runs only when that profile is
	 * activated. A POM whose only enforcer sits somewhere else therefore gets its own
	 * declaration in {@code build/plugins}.
	 */
	private Optional<Element> enforcerPluginOfTheBuild(PomDocument pom) {
		return buildPlugins(pom).filter(plugin -> PomDocument.hasArtifactId(plugin, ENFORCER_ARTIFACT_ID))
				.findFirst();
	}

	/**
	 * The plugins the POM's own {@code build} binds, which every build runs. Said once
	 * because {@link #alreadyRunsTheRule} and {@link #enforcerPluginOfTheBuild} ask
	 * about the same place for the same reason.
	 */
	private Stream<Element> buildPlugins(PomDocument pom) {
		return pom.at(BUILD_PLUGINS).stream()
				.flatMap(plugins -> PomDocument.children(plugins, "plugin").stream());
	}

	/**
	 * Adds the rule dependency and the execution to the {@code maven-enforcer-plugin}
	 * the build already declares — reusing its {@code dependencies} and
	 * {@code executions} when it has them — so the project keeps a single enforcer
	 * plugin entry and the rules it already ran keep running.
	 *
	 * <p>A plugin that already depends on {@value #RULE_ARTIFACT_ID} for a rule of its
	 * own keeps the dependency it declared, at the version it pinned: only the
	 * execution is missing, and adding a second dependency on the same artifact would
	 * leave the plugin's classpath deciding between two versions of it.
	 */
	private void augment(PomDocument pom, Element plugin, String execution) {
		requireRunnableEnforcer(pom, plugin);
		if (!declaresRuleDependency(plugin)) {
			pom.insertUnder(plugin, List.of("dependencies"), ruleDependency());
		}
		pom.insertUnder(plugin, List.of("executions"), execution);
	}

	/**
	 * Refuses to splice the execution into a {@value #ENFORCER_ARTIFACT_ID} the
	 * project pinned below {@value #MINIMUM_ENFORCER_VERSION}, which cannot resolve
	 * the rule by name: adding it anyway left the adoption advertising a guard that
	 * fails every build it runs in. Raising the project's pinned version instead is
	 * not the adoption's call — bumping it would change how the rules the project
	 * already runs behave, the same reason {@link CommitStep} refuses an ignored path
	 * rather than forcing the file in.
	 *
	 * <p>A declaration that names no version of its own is judged by the one the POM's
	 * own {@code pluginManagement} pins for it, because that is the version the build
	 * resolves — and pinning there while declaring the plugin bare in {@code build}
	 * is how Maven projects are ordinarily written, so reading only the declaration
	 * left this refusal blind to the common shape of the very thing it refuses. The
	 * execution was spliced in, committed, and left to {@link VerifyStep} to fail on,
	 * naming a rule Maven could not find rather than the declaration to raise.
	 *
	 * <p>Only a version literal is judged either way. One the POM leaves to a parent
	 * or to a property cannot be read from here, and refusing on that would turn the
	 * ordinary POM into an unadoptable one; see {@link PluginVersion}. A declaration
	 * this installer writes itself always pins {@value #ENFORCER_VERSION}, which no
	 * {@code pluginManagement} entry overrides, so only a plugin the project already
	 * had reaches this at all.
	 */
	private void requireRunnableEnforcer(PomDocument pom, Element plugin) {
		versionOf(plugin).ifPresentOrElse(
				version -> requireRunnable(version, IN_BUILD),
				() -> managedEnforcerVersion(pom).ifPresent(version -> requireRunnable(version, IN_PLUGIN_MANAGEMENT)));
	}

	/**
	 * The version the POM's own {@code pluginManagement} pins for the
	 * {@value #ENFORCER_ARTIFACT_ID}, which is what a declaration naming none resolves
	 * to.
	 */
	private Optional<String> managedEnforcerVersion(PomDocument pom) {
		return pom.at(MANAGED_PLUGINS).stream()
				.flatMap(plugins -> PomDocument.children(plugins, "plugin").stream())
				.filter(managed -> PomDocument.hasArtifactId(managed, ENFORCER_ARTIFACT_ID))
				.findFirst()
				.flatMap(PomEnforcerInstaller::versionOf);
	}

	/** @return the version literal the element names, or empty when it names none at all */
	private static Optional<String> versionOf(Element element) {
		return PomDocument.childText(element, "version").filter(version -> !version.isBlank());
	}

	/**
	 * @param version the version the build resolves the plugin at
	 * @param where   the declaration it was read from, so the refusal names the one to raise
	 */
	private void requireRunnable(String version, String where) {
		if (PluginVersion.isBelow(version, MINIMUM_ENFORCER_VERSION)) {
			throw new AdoptionException("Cannot wire the " + rules.ruleName() + " rule into the "
					+ ENFORCER_ARTIFACT_ID + " this project pins to " + version + " in " + where
					+ ": the rule is looked up by name,"
					+ " which the plugin only does from " + MINIMUM_ENFORCER_VERSION + ", so the guard would fail"
					+ " every build that runs it. Raise " + ENFORCER_ARTIFACT_ID + " to "
					+ MINIMUM_ENFORCER_VERSION + " or later, then adopt the repository again.");
		}
	}

	/** A freshly declared enforcer plugin: pinned to a version, carrying the rule and its execution. */
	private String plugin(String execution) {
		return PomDocument.wrapped("plugin", String.join("\n",
				element("groupId", ENFORCER_GROUP_ID),
				element("artifactId", ENFORCER_ARTIFACT_ID),
				element("version", ENFORCER_VERSION),
				PomDocument.wrapped("dependencies", ruleDependency()),
				PomDocument.wrapped("executions", execution)));
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
