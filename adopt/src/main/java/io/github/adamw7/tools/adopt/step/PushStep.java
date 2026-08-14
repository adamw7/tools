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
 * <p>The credentials the run was given are supplied to that one command rather
 * than read back from the checkout, because {@link CloneStep} deliberately does
 * not leave them there. They are passed as a {@code -c remote.origin.pushurl}
 * override, which git applies to this invocation alone and writes nowhere: pushing
 * to the URL positionally would publish the branch just as well but leaves git
 * unable to set an upstream, since a URL is not a remote to track.
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
