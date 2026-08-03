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
import io.github.adamw7.tools.adopt.Failures;
import io.github.adamw7.tools.adopt.command.CommandResult;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the Claude Code CLI in headless mode against the checkout so it generates a
 * {@code CLAUDE.md} for the project. The invocation is configurable because the
 * flags differ between environments; the default runs {@code /init}
 * non-interactively with {@code --permission-mode acceptEdits}, since headless
 * {@code -p} mode has no interactive approver and would otherwise only print a
 * request to write the file, exit cleanly, and leave nothing behind.
 *
 * <p>A repository that already carries a {@code .claude/CLAUDE.md} steers headless
 * {@code /init} into updating <em>that</em> file rather than the root one the
 * adoption needs — and a write under {@code .claude/} is refused as a sensitive
 * path — so that memory file is moved aside before the run and restored
 * afterwards, on the failure path too. A run that exits cleanly but leaves no
 * {@code CLAUDE.md} behind aborts the adoption rather than pushing an empty pull
 * request.
 *
 * <p>The step is idempotent: a checkout that already carries a root
 * {@code CLAUDE.md} is left alone, because the CLI's output is not reproducible
 * and regenerating would discard whatever has been edited since.
 */
public class ClaudeInitStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(ClaudeInitStep.class);

	static final List<String> DEFAULT_COMMAND = List.of("claude", "-p", "/init",
			"--permission-mode", "acceptEdits");

	private static final String CLAUDE_MD = AdoptionAssets.CLAUDE_MD_FILE;
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
		if (Files.isRegularFile(checkout.resolve(CLAUDE_MD))) {
			log.info("{} already carries {}; left unchanged", checkout, CLAUDE_MD);
			return;
		}
		log.info("Running claude init in {}", checkout);
		Optional<Path> relocated = relocateExistingClaudeDirMemory(checkout);
		CommandResult result;
		try {
			result = runOrFail(runner, checkout, claudeCommand);
		} catch (RuntimeException e) {
			Failures.alsoRun(e, () -> restoreRelocated(relocated, checkout));
			throw e;
		}
		restoreRelocated(relocated, checkout);
		requireGenerated(context, result);
	}

	private void restoreRelocated(Optional<Path> relocated, Path checkout) {
		relocated.ifPresent(backup -> restore(backup, claudeDirMemory(checkout)));
	}

	private Path claudeDirMemory(Path checkout) {
		return checkout.resolve(CLAUDE_DIR).resolve(CLAUDE_MD);
	}

	/**
	 * Moves an existing {@code .claude/CLAUDE.md} out of the checkout, into the
	 * system temp directory so a later {@code git add -A} cannot pick it up.
	 *
	 * @return the backup location, or empty when the checkout carried no such file
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

	/**
	 * The CLI's own transcript is carried into the failure, because a run that exits
	 * cleanly without writing the file has said why in it — it declined, it asked a
	 * question headless mode could not answer, it wrote somewhere else — and that
	 * transcript is otherwise discarded, {@link #runOrFail} only reporting the output
	 * of a command that failed. Without it the adoption stops on a message that names
	 * the missing file and nothing that would explain it.
	 */
	private void requireGenerated(AdoptionContext context, CommandResult result) {
		if (!Files.isRegularFile(context.repositoryDirectory().resolve(CLAUDE_MD))) {
			throw new AdoptionException(name() + " completed but " + CLAUDE_MD + " was not found in "
					+ context.repositoryDirectory() + ". " + CommandResult.describe(claudeCommand)
					+ " exited " + result.exitCode() + " and said:" + System.lineSeparator()
					+ result.redactedOutput().strip());
		}
	}
}
