package io.github.adamw7.tools.adopt;

/**
 * Runs the clean-up an adoption owes while a failure is already propagating —
 * restoring a file that was moved aside, writing the report of a run that did
 * not finish — without letting the clean-up's own failure replace the one being
 * reported.
 *
 * <p>The original failure is the diagnostic the operator needs: it carries the
 * {@code claude} transcript, or the step that stopped the pipeline. A clean-up
 * thrown from a {@code finally} would discard it and leave only the secondary
 * error behind, so the secondary is attached as a suppressed exception instead
 * and both survive.
 */
public final class Failures {

	private Failures() {
	}

	/**
	 * Runs {@code cleanUp}, attaching any failure it raises to {@code failure} as a
	 * suppressed exception. The caller keeps throwing {@code failure} afterwards.
	 */
	public static void alsoRun(RuntimeException failure, Runnable cleanUp) {
		try {
			cleanUp.run();
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
	}

	/**
	 * @return the failure's message, falling back to its type when it carries none,
	 *         so a report never records a bare {@code null} as the reason a run
	 *         stopped
	 */
	public static String describe(RuntimeException failure) {
		String message = failure.getMessage();
		return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
	}
}
