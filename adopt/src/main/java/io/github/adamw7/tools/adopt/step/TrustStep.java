package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Marks the cloned checkout as trusted in Claude Code's configuration before
 * {@link ClaudeInitStep} runs {@code claude} there, so the headless invocation
 * is not blocked by the interactive folder-trust prompt. Trust is keyed to the
 * exact directory {@code claude} starts in, so the checkout itself is trusted
 * rather than its parent workspace.
 */
public class TrustStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(TrustStep.class);

	private final ClaudeTrustStore trustStore;

	public TrustStep() {
		this(new ClaudeTrustStore());
	}

	public TrustStep(ClaudeTrustStore trustStore) {
		this.trustStore = trustStore;
	}

	@Override
	public String name() {
		return "trust";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path directory = context.repositoryDirectory();
		if (trustStore.trust(directory)) {
			log.info("Marked {} as trusted for Claude Code", directory);
		} else {
			log.info("{} is already trusted for Claude Code; left unchanged", directory);
		}
	}
}
