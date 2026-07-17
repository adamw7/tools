package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the Claude Code CLI in headless mode against the checkout so it
 * generates a {@code CLAUDE.md} for the project. The exact CLI invocation is
 * configurable because the flags differ between environments; the default runs
 * the {@code /init} command non-interactively.
 */
public class ClaudeInitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(ClaudeInitStep.class);

	static final List<String> DEFAULT_COMMAND = List.of("claude", "-p", "/init");

	private static final String CLAUDE_MD = "CLAUDE.md";

	private final List<String> claudeCommand;

	public ClaudeInitStep() {
		this(DEFAULT_COMMAND);
	}

	public ClaudeInitStep(List<String> claudeCommand) {
		this.claudeCommand = List.copyOf(claudeCommand);
	}

	@Override
	public String name() {
		return "claude-init";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Running claude init in {}", context.repositoryDirectory());
		runOrFail(runner, context.repositoryDirectory(), claudeCommand);
		warnIfNotGenerated(context);
	}

	private void warnIfNotGenerated(AdoptionContext context) {
		if (!Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD))) {
			log.warn("claude init completed but {} was not found in {}", CLAUDE_MD,
					context.repositoryDirectory());
		}
	}
}
