package io.github.adamw7.tools.enforcer.project;

import java.io.File;

import io.github.adamw7.tools.markdown.MarkdownText;

/**
 * Where Claude Code keeps a project's configuration, so a composite rule can find
 * it from the project directory alone. Every path here is the one Claude Code
 * itself uses, which is what lets {@link ClaudeCodeProjectRule} be configured with
 * a single {@code projectDir} instead of the sixty-odd elements naming each rule's
 * inputs; stating the convention once keeps two parts from disagreeing about where
 * the skills live.
 *
 * @param projectDir the project's root, which every other path is resolved against
 */
public record ProjectLayout(File projectDir) {

	private static final String CLAUDE_DIR = ".claude";

	public File claudeMd() {
		return new File(projectDir, "CLAUDE.md");
	}

	public File agentsMd() {
		return new File(projectDir, "AGENTS.md");
	}

	public File readme() {
		return new File(projectDir, "README.md");
	}

	public File pom() {
		return new File(projectDir, "pom.xml");
	}

	public File gitignore() {
		return new File(projectDir, ".gitignore");
	}

	public File mcpJson() {
		return new File(projectDir, ".mcp.json");
	}

	public File settingsJson() {
		return new File(claudeDir(), "settings.json");
	}

	public File skillsDir() {
		return new File(claudeDir(), "skills");
	}

	public File agentsDir() {
		return new File(claudeDir(), "agents");
	}

	public File commandsDir() {
		return new File(claudeDir(), "commands");
	}

	public File hooksDir() {
		return new File(claudeDir(), "hooks");
	}

	public File pluginJson() {
		return new File(new File(projectDir, ".claude-plugin"), "plugin.json");
	}

	public File okfBundleDir() {
		return new File(projectDir, "okf");
	}

	/**
	 * Whether {@link #pom()} is an aggregator, and so whether there is a module map
	 * at all. A single-module pom declares no {@code <module>}, and the rule pointed
	 * at one answers that it was pointed at the wrong pom — the right answer to a
	 * configured path and the wrong one to a convention. A text search rather than a
	 * parse, because the question is only whether the element occurs; a pom that
	 * cannot be read is not an aggregator, and the rule that reads it will say why.
	 */
	public boolean declaresModules() {
		return MarkdownText.readIfText(pom()).filter(pom -> pom.contains("<module>")).isPresent();
	}

	private File claudeDir() {
		return new File(projectDir, CLAUDE_DIR);
	}
}
