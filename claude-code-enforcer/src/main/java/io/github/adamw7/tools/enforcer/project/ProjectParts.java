package io.github.adamw7.tools.enforcer.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.github.adamw7.tools.enforcer.definition.CommandFormatRule;
import io.github.adamw7.tools.enforcer.definition.SkillFilesExistRule;
import io.github.adamw7.tools.enforcer.definition.SubAgentFormatRule;
import io.github.adamw7.tools.enforcer.definition.UniqueDescriptionsRule;
import io.github.adamw7.tools.enforcer.definition.UniqueNamesRule;
import io.github.adamw7.tools.enforcer.doc.AgentsMdFormatRule;
import io.github.adamw7.tools.enforcer.doc.ClaudeMdFormatRule;
import io.github.adamw7.tools.enforcer.doc.ContextBudgetRule;
import io.github.adamw7.tools.enforcer.doc.MemoryImportsRule;
import io.github.adamw7.tools.enforcer.doc.ModuleMapConsistencyRule;
import io.github.adamw7.tools.enforcer.mcp.McpConfigFormatRule;
import io.github.adamw7.tools.enforcer.mcp.McpServersValidRule;
import io.github.adamw7.tools.enforcer.okf.OkfBundleFormatRule;
import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;
import io.github.adamw7.tools.enforcer.secret.NoSecretsRule;
import io.github.adamw7.tools.enforcer.settings.HookCommandsValidRule;
import io.github.adamw7.tools.enforcer.settings.HooksFormatRule;
import io.github.adamw7.tools.enforcer.settings.LocalSettingsIgnoredRule;
import io.github.adamw7.tools.enforcer.settings.PermissionsFormatRule;
import io.github.adamw7.tools.enforcer.settings.SettingsJsonValidRule;

/**
 * Assembles the rules {@link ClaudeCodeProjectRule} runs, each pointed at the
 * conventional path {@link ProjectLayout} gives it.
 *
 * <p>A part is included only when the input it reads is present. That is the
 * difference between this and wiring the rules by hand: a rule configured with a
 * directory fails on an absent one, because somebody named a path that is not
 * there — whereas nothing was named here, so an absent {@code .claude/commands}
 * means a project with no slash commands. A part starts checking the day the
 * project grows the thing it checks. The module map is checked only where the pom
 * declares one, for the same reason.
 *
 * <p>{@code contextBudget} is the one part no file can size, so the composite
 * supplies the convention — a {@code CLAUDE.md} is loaded into every session, so
 * it is meant to be a summary — and a project that disagrees sets its own or
 * turns the part off with a budget of zero.
 *
 * <p>{@code crossDocConsistency} and {@code readmeConsistency} are deliberately
 * absent: they take the patterns a particular project needs kept in step, and
 * there is no convention to default those to. A project that wants them wires
 * them beside this rule.
 */
final class ProjectParts {

	private final ProjectLayout layout;
	private final DocumentContract contract;

	ProjectParts(ProjectLayout layout, DocumentContract contract) {
		this.layout = layout;
		this.contract = contract;
	}

	/**
	 * @return every part whose input is present, in a fixed order so a run's report
	 *         and a recorded baseline read the same way twice
	 */
	List<ClaudeCodeEnforcerRule> present() {
		List<ClaudeCodeEnforcerRule> parts = new ArrayList<>();
		addDocumentParts(parts);
		addDefinitionParts(parts);
		addSettingsParts(parts);
		addFileParts(parts);
		return parts;
	}

	private void addDocumentParts(List<ClaudeCodeEnforcerRule> parts) {
		File claudeMd = layout.claudeMd();
		File agentsMd = layout.agentsMd();
		ifFile(parts, claudeMd, ClaudeMdFormatRule::new, rule -> configureClaudeMd(rule, claudeMd));
		ifFile(parts, agentsMd, AgentsMdFormatRule::new, rule -> {
			rule.setAgentsMdFile(agentsMd);
			rule.setAutoFix(contract.autoFix());
		});
		ifFile(parts, claudeMd, MemoryImportsRule::new, rule -> rule.setClaudeMdFile(claudeMd));
		if (contract.budgetBytes() > 0) {
			ifFile(parts, claudeMd, ContextBudgetRule::new, rule -> {
				rule.setFiles(List.of(claudeMd));
				rule.setMaxBytes(contract.budgetBytes());
			});
		}
		if (layout.declaresModules()) {
			ifFile(parts, layout.pom(), ModuleMapConsistencyRule::new, rule -> {
				rule.setPomFile(layout.pom());
				rule.setDocFiles(presentOf(claudeMd, agentsMd));
			});
		}
	}

	private void configureClaudeMd(ClaudeMdFormatRule rule, File claudeMd) {
		rule.setClaudeMdFile(claudeMd);
		rule.setAutoFix(contract.autoFix());
		if (contract.claudeMdSections() != null) {
			rule.setRequiredSections(contract.claudeMdSections());
		}
		if (contract.claudeMdReference() != null) {
			rule.setRequiredReference(contract.claudeMdReference());
		}
	}

	private void addDefinitionParts(List<ClaudeCodeEnforcerRule> parts) {
		ifDirectory(parts, layout.skillsDir(), SkillFilesExistRule::new,
				rule -> rule.setSkillsDir(layout.skillsDir()));
		ifDirectory(parts, layout.agentsDir(), SubAgentFormatRule::new,
				rule -> rule.setAgentsDir(layout.agentsDir()));
		ifDirectory(parts, layout.commandsDir(), CommandFormatRule::new,
				rule -> rule.setCommandsDir(layout.commandsDir()));
		addUniquenessParts(parts);
	}

	/**
	 * Both uniqueness rules see all three kinds at once — a command sharing a name or
	 * a description with a skill shadows it, and neither directory alone would show
	 * that — so they are included when any one of the three directories exists, each
	 * pointed only at the directories that do.
	 */
	private void addUniquenessParts(List<ClaudeCodeEnforcerRule> parts) {
		if (!anyDefinitionDirectory()) {
			return;
		}
		UniqueNamesRule names = new UniqueNamesRule();
		pointAtDefinitions(names::setCommandsDir, names::setAgentsDir, names::setSkillsDir);
		parts.add(names);
		UniqueDescriptionsRule descriptions = new UniqueDescriptionsRule();
		pointAtDefinitions(descriptions::setCommandsDir, descriptions::setAgentsDir, descriptions::setSkillsDir);
		parts.add(descriptions);
	}

	private void pointAtDefinitions(Consumer<File> commands, Consumer<File> agents, Consumer<File> skills) {
		ifDirectory(layout.commandsDir(), commands);
		ifDirectory(layout.agentsDir(), agents);
		ifDirectory(layout.skillsDir(), skills);
	}

	private void addSettingsParts(List<ClaudeCodeEnforcerRule> parts) {
		File settings = layout.settingsJson();
		ifFile(parts, settings, SettingsJsonValidRule::new, rule -> rule.setSettingsFile(settings));
		ifFile(parts, settings, PermissionsFormatRule::new, rule -> rule.setSettingsFile(settings));
		ifFile(parts, settings, HookCommandsValidRule::new, rule -> {
			rule.setSettingsFile(settings);
			rule.setProjectDir(layout.projectDir());
		});
		ifDirectory(parts, layout.hooksDir(), HooksFormatRule::new, rule -> {
			rule.setHooksDir(layout.hooksDir());
			rule.setSettingsFile(settings);
			rule.setProjectDir(layout.projectDir());
		});
		ifFile(parts, layout.gitignore(), LocalSettingsIgnoredRule::new,
				rule -> rule.setGitignoreFile(layout.gitignore()));
	}

	private void addFileParts(List<ClaudeCodeEnforcerRule> parts) {
		ifFile(parts, layout.mcpJson(), McpServersValidRule::new, rule -> rule.setMcpFile(layout.mcpJson()));
		ifFile(parts, layout.mcpJson(), McpConfigFormatRule::new, rule -> rule.setMcpFile(layout.mcpJson()));
		ifDirectory(parts, layout.okfBundleDir(), OkfBundleFormatRule::new,
				rule -> rule.setBundleDir(layout.okfBundleDir()));
		addSecretsPart(parts);
	}

	/**
	 * The secret scan is pointed at every configuration file and directory the
	 * project actually has, and included when it has any of them: a credential
	 * committed into a hook is the finding that matters most, and a scan configured
	 * with a directory that is not there would fail rather than scan the rest.
	 */
	private void addSecretsPart(List<ClaudeCodeEnforcerRule> parts) {
		List<File> files = presentOf(layout.settingsJson(), layout.mcpJson());
		List<File> directories = presentDirectoriesOf(layout.hooksDir(), layout.agentsDir(), layout.commandsDir(),
				layout.skillsDir());
		if (files.isEmpty() && directories.isEmpty()) {
			return;
		}
		NoSecretsRule rule = new NoSecretsRule();
		rule.setFiles(files);
		rule.setDirectories(directories);
		parts.add(rule);
	}

	private boolean anyDefinitionDirectory() {
		return layout.commandsDir().isDirectory() || layout.agentsDir().isDirectory()
				|| layout.skillsDir().isDirectory();
	}

	private void ifDirectory(File directory, Consumer<File> setter) {
		if (directory.isDirectory()) {
			setter.accept(directory);
		}
	}

	/** Adds the part, configured, when the file it reads is there. */
	private <R extends ClaudeCodeEnforcerRule> void ifFile(List<ClaudeCodeEnforcerRule> parts, File file,
			Supplier<R> part, Consumer<R> configure) {
		if (file.isFile()) {
			parts.add(configured(part, configure));
		}
	}

	/** Adds the part, configured, when the directory it scans is there. */
	private <R extends ClaudeCodeEnforcerRule> void ifDirectory(List<ClaudeCodeEnforcerRule> parts, File directory,
			Supplier<R> part, Consumer<R> configure) {
		if (directory.isDirectory()) {
			parts.add(configured(part, configure));
		}
	}

	private <R extends ClaudeCodeEnforcerRule> R configured(Supplier<R> part, Consumer<R> configure) {
		R rule = part.get();
		configure.accept(rule);
		return rule;
	}

	private List<File> presentOf(File... candidates) {
		return Arrays.stream(candidates).filter(File::isFile).toList();
	}

	private List<File> presentDirectoriesOf(File... candidates) {
		return Arrays.stream(candidates).filter(File::isDirectory).toList();
	}
}
