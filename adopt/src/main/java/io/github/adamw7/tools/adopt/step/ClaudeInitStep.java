package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

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
 * <p>A repository that already carries a {@code .claude/CLAUDE.md} steers
 * headless {@code /init} into <em>updating that file</em> rather than writing the
 * root {@code CLAUDE.md} the adoption needs — and a write under {@code .claude/}
 * is refused as a sensitive path in headless mode, so the run exits cleanly
 * having produced nothing. That existing memory file is therefore moved out of
 * the checkout before {@code /init} runs, so the CLI takes its fresh-repository
 * path and writes the root {@code CLAUDE.md}, and restored afterwards; the
 * adoption commits only the new root file and leaves the project's own
 * {@code .claude/CLAUDE.md} untouched.
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
	private static final String CLAUDE_DIR = ".claude";

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
		Path checkout = context.repositoryDirectory();
		log.info("Running claude init in {}", checkout);
		Optional<Path> relocated = relocateExistingClaudeDirMemory(checkout);
		try {
			runOrFail(runner, checkout, claudeCommand);
		} finally {
			relocated.ifPresent(backup -> restore(backup, claudeDirMemory(checkout)));
		}
		requireGenerated(context);
	}

	private Path claudeDirMemory(Path checkout) {
		return checkout.resolve(CLAUDE_DIR).resolve(CLAUDE_MD);
	}

	/**
	 * Moves an existing {@code .claude/CLAUDE.md} out of the checkout so headless
	 * {@code /init} writes the root {@code CLAUDE.md} instead of trying — and
	 * failing — to update the sensitive-path memory file. The backup lands in the
	 * system temp directory rather than the checkout so a later {@code git add -A}
	 * cannot pick it up.
	 *
	 * @return the backup location when a memory file was moved aside, empty when
	 *         the checkout carried none and nothing had to be relocated
	 */
	private Optional<Path> relocateExistingClaudeDirMemory(Path checkout) {
		Path memory = claudeDirMemory(checkout);
		if (!Files.isRegularFile(memory)) {
			return Optional.empty();
		}
		return Optional.of(moveAside(memory));
	}

	private Path moveAside(Path memory) {
		try {
			Path backup = Files.createTempFile("adopt-claude-dir-memory-", ".bak");
			Files.move(memory, backup, StandardCopyOption.REPLACE_EXISTING);
			log.info("Moved existing {} aside so /init writes the root {}", memory, CLAUDE_MD);
			return backup;
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not move aside existing " + memory, e);
		}
	}

	private void restore(Path backup, Path memory) {
		try {
			Files.createDirectories(memory.getParent());
			Files.move(backup, memory, StandardCopyOption.REPLACE_EXISTING);
			log.info("Restored existing {}", memory);
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not restore " + memory + " from " + backup, e);
		}
	}

	private void requireGenerated(AdoptionContext context) {
		if (!Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD))) {
			throw new AdoptionException(name() + " completed but " + CLAUDE_MD + " was not found in "
					+ context.repositoryDirectory());
		}
	}
}
