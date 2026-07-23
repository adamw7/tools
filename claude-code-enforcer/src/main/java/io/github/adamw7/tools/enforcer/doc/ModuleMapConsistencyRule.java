package io.github.adamw7.tools.enforcer.doc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Enforcer rule that keeps the module map in the agent docs from drifting away
 * from the real Maven reactor. {@link CrossDocConsistencyRule} pins scalar
 * facts with regular expressions, but a module added to the root {@code pom.xml}
 * and never documented is a structural gap no single pattern expresses: an
 * agent reading the docs simply never learns the module exists. This rule
 * extracts every {@code <module>} entry from the configured {@code pomFile}
 * (XML comments are ignored, so a commented-out module does not count) and
 * fails when a module's name — the last path segment, for a nested entry such
 * as {@code code/context} — does not appear in each configured doc file.
 * <p>
 * The check is presence-only by design: how a doc arranges its module map is
 * prose, but every live module must at least be mentioned. Modules listed in
 * {@code ignoredModules} are exempt, e.g. an internal test-only module the docs
 * deliberately leave out. A pom with no {@code <module>} entries fails, because
 * pointing this rule at a non-aggregator pom is a build-setup mistake. All
 * missing mentions are reported together.
 */
@Named("moduleMapConsistency")
public class ModuleMapConsistencyRule extends ClaudeCodeEnforcerRule {

	private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
	private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");

	/** The aggregator {@code pom.xml} whose {@code <module>} entries are the source of truth. */
	private File pomFile;

	/** The documentation files that must mention every module, e.g. CLAUDE.md and AGENTS.md. */
	private List<File> docFiles;

	/** Optional module names the docs may leave undocumented. */
	private List<String> ignoredModules;

	@Override
	public void execute() throws EnforcerRuleException {
		requireConfigured(pomFile, "pomFile");
		requireExists(pomFile, "pom.xml");
		requireDocsConfigured();
		List<String> modules = modules(requireContent(pomFile, "pom.xml"));
		requireModules(modules);
		List<String> violations = new ArrayList<>();
		for (File docFile : docFiles) {
			collectDocViolations(docFile, modules, violations);
		}
		report("Documented module map is out of date:", violations);
	}

	@Override
	protected List<String> howToFix() {
		return List.of(
				"Open each doc file listed above.",
				"Add the missing module to its module map, or list the module under ignoredModules if it is deliberately undocumented.",
				"Re-run the build to confirm the docs cover every reactor module.");
	}

	private void requireDocsConfigured() throws EnforcerRuleException {
		requireConfigured(docFiles, "docFiles");
		if (docFiles.isEmpty()) {
			throw new EnforcerRuleException("The docFiles parameter must list at least one file");
		}
	}

	private void requireModules(List<String> modules) throws EnforcerRuleException {
		if (modules.isEmpty()) {
			throw new EnforcerRuleException(pomFile + " declares no <module> entries; point pomFile at the aggregator pom");
		}
	}

	private List<String> modules(String pomContent) {
		Matcher matcher = MODULE.matcher(XML_COMMENT.matcher(pomContent).replaceAll(""));
		List<String> modules = new ArrayList<>();
		while (matcher.find()) {
			modules.add(matcher.group(1));
		}
		return modules;
	}

	private void collectDocViolations(File docFile, List<String> modules, List<String> violations)
			throws EnforcerRuleException {
		requireExists(docFile, docFile.getName());
		String content = requireContent(docFile, docFile.getName());
		for (String module : modules) {
			addMissingMention(docFile, module, content, violations);
		}
	}

	private void addMissingMention(File docFile, String module, String content, List<String> violations) {
		String name = moduleName(module);
		if (!isIgnored(name) && !content.contains(name)) {
			violations.add(docFile + " does not mention module '" + name + "' declared in " + pomFile);
		}
	}

	/** The last path segment, so a nested reactor entry such as {@code code/context} is looked up as {@code context}. */
	private String moduleName(String module) {
		int slash = module.lastIndexOf('/');
		return slash < 0 ? module : module.substring(slash + 1);
	}

	private boolean isIgnored(String name) {
		return ignoredModules != null && ignoredModules.contains(name);
	}

	void setPomFile(File pomFile) {
		this.pomFile = pomFile;
	}

	void setDocFiles(List<File> docFiles) {
		this.docFiles = docFiles;
	}

	void setIgnoredModules(List<String> ignoredModules) {
		this.ignoredModules = ignoredModules;
	}

	@Override
	public String toString() {
		return String.format("ModuleMapConsistencyRule[pomFile=%s, docFiles=%s]", pomFile, docFiles);
	}
}
