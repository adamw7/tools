package io.github.adamw7.tools.adopt.step;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Pushes the adoption feature branch to the repository's origin with
 * {@code git push -u origin <branch>}, setting the upstream so the freshly
 * created branch is published and can be the head of a pull request.
 *
 * <p>The credentials are supplied to that one command rather than read back from the
 * checkout, {@link CloneStep} deliberately not leaving them there. They go as a
 * {@code -c remote.origin.pushurl} override, applied to this invocation alone and
 * written nowhere: pushing to the URL positionally publishes the branch but leaves
 * git unable to set an upstream, a URL not being a remote to track.
 */
public class PushStep extends AbstractCommandStep {

	private static final Logger log = LogManager.getLogger(PushStep.class);

	/** The configuration key git resolves a push through, in preference to the remote's URL. */
	private static final String PUSH_URL = "remote." + AdoptionContext.REMOTE + ".pushurl=";

	@Override
	public String name() {
		return "push";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		log.info("Pushing branch {} from {}", context.branchName(), context.repositoryDirectory());
		List<String> command = List.of("git", "-c", PUSH_URL + context.repositoryUrl(),
				"push", "-u", AdoptionContext.REMOTE, context.branchName());
		runOrFail(runner, context.repositoryDirectory(), command);
	}
}
