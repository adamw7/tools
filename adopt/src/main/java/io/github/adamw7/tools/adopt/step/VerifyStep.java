package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Runs the adopted project's {@code validate} phase so the freshly wired
 * {@code claude-code-enforcer} actually executes against the generated
 * {@code CLAUDE.md} before the branch is pushed and a pull request is opened. A
 * malformed or missing {@code CLAUDE.md} therefore fails the adoption locally
 * instead of breaking the contributor's build after the pull request lands.
 *
 * <p>The enforcer execution is bound to the root module's {@code validate} phase
 * with {@code inherited=false}, so a non-recursive {@code mvn -N validate} is
 * enough to exercise it. The Maven invocation is configurable because it differs
 * between environments. A repository that is not a Maven project (no
 * {@code pom.xml}) has nothing to verify and is skipped, mirroring
 * {@link EnforcerStep}.
 */
public class VerifyStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(VerifyStep.class);

	static final List<String> DEFAULT_COMMAND = List.of("mvn", "-q", "-N", "validate");

	private static final String POM = "pom.xml";

	private final List<String> mavenCommand;

	public VerifyStep() {
		this(DEFAULT_COMMAND);
	}

	public VerifyStep(List<String> mavenCommand) {
		this.mavenCommand = List.copyOf(mavenCommand);
	}

	@Override
	public String name() {
		return "verify";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path pomFile = context.repositoryDirectory().resolve(POM);
		if (Files.isRegularFile(pomFile)) {
			log.info("Verifying the enforcer passes in {}", context.repositoryDirectory());
			runOrFail(runner, context.repositoryDirectory(), mavenCommand);
		} else {
			log.warn("No {} in {}; skipping build verification", POM, context.repositoryDirectory());
		}
	}
}
