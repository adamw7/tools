package io.github.adamw7.tools.enforcer.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * <p>A part is included only when the input it reads is actually there. That is
 * the difference between this and wiring the rules by hand: a rule configured
 * with a directory treats an absent one as a build-setup mistake and fails,
 * because somebody named a path that is not there — whereas nothing was named
 * here, so an absent {@code .claude/commands} means a project with no slash
 * commands, which is not a fault. A part therefore starts checking the day the
 * project grows the thing it checks, with no configuration to remember.
 *
 * <p>The module map is checked only where there is one: a single-module project's
 * {@code pom.xml} declares no {@code <module>}, and the rule pointed at it says so
 * rather than comparing anything. Configured by hand that message is right — the
 * operator named an aggregator that is not one — but here nothing named it, so the
 * part is simply not applicable and is left out.
 *
 * <p>{@code contextBudget} is the one part the project's own files cannot size: it
 * needs a limit, and there is no file to read one from. The composite supplies the
 * convention instead — a {@code CLAUDE.md} is loaded into every session, so it is
 * meant to be a summary — and a project that disagrees sets its own or turns the
 * part off with a budget of zero.
 *
 * <p>Two shipped rules are deliberately absent: {@code crossDocConsistency} and
 * {@code readmeConsistency} are configured with the patterns a particular project
 * needs kept in step — a Java version, a protobuf major — and there is no
 * convention to default those to. A project that wants them wires them beside
 * this rule, which is why the composite is an addition to the catalogue rather
 * than a replacement for it.
 */
final class ProjectParts {

	private final ProjectLayout layout;
	private final boolean autoFix;
	private final int claudeMdBudgetBytes;

	ProjectParts(ProjectLayout layout, boolean autoFix, int claudeMdBudgetBytes) {
		this.layout = layout;
		this.autoFix = autoFix;
		this.claudeMdBudgetBytes = claudeMdBudgetBytes;
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
		whenFile(parts, layout.claudeMd(), () -> {
			ClaudeMdFormatRule rule = new ClaudeMdFormatRule();
			rule.setClaudeMdFile(layout.claudeMd());
			rule.setAutoFix(autoFix);
			return rule;
		});
		whenFile(parts, layout.agentsMd(), () -> {
			AgentsMdFormatRule rule = new AgentsMdFormatRule();
			rule.setAgentsMdFile(layout.agentsMd());
			rule.setAutoFix(autoFix);
			return rule;
		});
		whenFile(parts, layout.claudeMd(), () -> {
			MemoryImportsRule rule = new MemoryImportsRule();
			rule.setClaudeMdFile(layout.claudeMd());
			return rule;
		});
		if (claudeMdBudgetBytes > 0) {
			whenFile(parts, layout.claudeMd(), () -> {
				ContextBudgetRule rule = new ContextBudgetRule();
				rule.setFiles(List.of(layout.claudeMd()));
				rule.setMaxBytes(claudeMdBudgetBytes);
				return rule;
			});
		}
		if (layout.declaresModules()) {
			whenFile(parts, layout.pom(), () -> {
				ModuleMapConsistencyRule rule = new ModuleMapConsistencyRule();
				rule.setPomFile(layout.pom());
				rule.setDocFiles(presentOf(layout.claudeMd(), layout.agentsMd()));
				return rule;
			});
		}
	}

	private void addDefinitionParts(List<ClaudeCodeEnforcerRule> parts) {
		whenDirectory(parts, layout.skillsDir(), () -> {
			SkillFilesExistRule rule = new SkillFilesExistRule();
			rule.setSkillsDir(layout.skillsDir());
			return rule;
		});
		whenDirectory(parts, layout.agentsDir(), () -> {
			SubAgentFormatRule rule = new SubAgentFormatRule();
			rule.setAgentsDir(layout.agentsDir());
			return rule;
		});
		whenDirectory(parts, layout.commandsDir(), () -> {
			CommandFormatRule rule = new CommandFormatRule();
			rule.setCommandsDir(layout.commandsDir());
			return rule;
		});
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

	private void pointAtDefinitions(DirectorySetter commands, DirectorySetter agents, DirectorySetter skills) {
		ifDirectory(layout.commandsDir(), commands);
		ifDirectory(layout.agentsDir(), agents);
		ifDirectory(layout.skillsDir(), skills);
	}

	private void addSettingsParts(List<ClaudeCodeEnforcerRule> parts) {
		whenFile(parts, layout.settingsJson(), () -> {
			SettingsJsonValidRule rule = new SettingsJsonValidRule();
			rule.setSettingsFile(layout.settingsJson());
			return rule;
		});
		whenFile(parts, layout.settingsJson(), () -> {
			PermissionsFormatRule rule = new PermissionsFormatRule();
			rule.setSettingsFile(layout.settingsJson());
			return rule;
		});
		whenFile(parts, layout.settingsJson(), () -> {
			HookCommandsValidRule rule = new HookCommandsValidRule();
			rule.setSettingsFile(layout.settingsJson());
			rule.setProjectDir(layout.projectDir());
			return rule;
		});
		whenDirectory(parts, layout.hooksDir(), () -> {
			HooksFormatRule rule = new HooksFormatRule();
			rule.setHooksDir(layout.hooksDir());
			rule.setSettingsFile(layout.settingsJson());
			rule.setProjectDir(layout.projectDir());
			return rule;
		});
		whenFile(parts, layout.gitignore(), () -> {
			LocalSettingsIgnoredRule rule = new LocalSettingsIgnoredRule();
			rule.setGitignoreFile(layout.gitignore());
			return rule;
		});
	}

	private void addFileParts(List<ClaudeCodeEnforcerRule> parts) {
		whenFile(parts, layout.mcpJson(), () -> {
			McpServersValidRule rule = new McpServersValidRule();
			rule.setMcpFile(layout.mcpJson());
			return rule;
		});
		whenFile(parts, layout.mcpJson(), () -> {
			McpConfigFormatRule rule = new McpConfigFormatRule();
			rule.setMcpFile(layout.mcpJson());
			return rule;
		});
		whenDirectory(parts, layout.okfBundleDir(), () -> {
			OkfBundleFormatRule rule = new OkfBundleFormatRule();
			rule.setBundleDir(layout.okfBundleDir());
			return rule;
		});
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

	private void ifDirectory(File directory, DirectorySetter setter) {
		if (directory.isDirectory()) {
			setter.set(directory);
		}
	}

	private void whenFile(List<ClaudeCodeEnforcerRule> parts, File file,
			Supplier<ClaudeCodeEnforcerRule> part) {
		if (file.isFile()) {
			parts.add(part.get());
		}
	}

	private void whenDirectory(List<ClaudeCodeEnforcerRule> parts, File directory,
			Supplier<ClaudeCodeEnforcerRule> part) {
		if (directory.isDirectory()) {
			parts.add(part.get());
		}
	}

	private List<File> presentOf(File... candidates) {
		return Arrays.stream(candidates).filter(File::isFile).toList();
	}

	private List<File> presentDirectoriesOf(File... candidates) {
		return Arrays.stream(candidates).filter(File::isDirectory).toList();
	}

	/** Named so the three uniqueness directories are pointed at through one shape. */
	@FunctionalInterface
	private interface DirectorySetter {
		void set(File directory);
	}
}
