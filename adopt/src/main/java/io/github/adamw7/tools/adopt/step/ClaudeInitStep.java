package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the Claude Code CLI in headless mode against the checkout so it
 * generates a {@code CLAUDE.md} for the project. The exact CLI invocation is
 * configurable because the flags differ between environments; the default runs
 * the {@code /init} command non-interactively with {@code --permission-mode
 * acceptEdits} so the CLI may write the file. Headless {@code -p} mode has no
 * interactive approver, so without a permission mode that pre-approves edits the
 * {@code /init} command only prints a request to write {@code CLAUDE.md}, exits
 * cleanly, and leaves nothing behind.
 *
 * <p>The generated {@code CLAUDE.md} is the whole point of the adoption, so a run
 * that exits cleanly but leaves no {@code CLAUDE.md} behind aborts the adoption
 * rather than letting the pipeline push a branch and open a pull request with
 * nothing in it.
 */
public class ClaudeInitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(ClaudeInitStep.class);

	static final List<String> DEFAULT_COMMAND = List.of("claude", "-p", "/init",
			"--permission-mode", "acceptEdits");

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
		requireGenerated(context);
	}

	private void requireGenerated(AdoptionContext context) {
		if (!Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD))) {
			throw new AdoptionException(name() + " completed but " + CLAUDE_MD + " was not found in "
					+ context.repositoryDirectory());
		}
	}
}
