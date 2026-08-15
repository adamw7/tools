package io.github.adamw7.tools.enforcer.e2e;

/**
 * What a process {@link LoggedProcess} ran said and whether it passed — the whole
 * of what an end-to-end test can observe, since a fixture build, like the clone
 * that fetches the project it is pointed at, runs in its own process.
 *
 * @param exitCode the process exit code, zero when it succeeded
 * @param output   the merged standard output and standard error of the process
 */
record BuildOutcome(int exitCode, String output) {

	boolean succeeded() {
		return exitCode == 0;
	}

	boolean mentions(String text) {
		return output.contains(text);
	}

	/**
	 * The whole build log, for the {@code assert*} message of a test that did not get
	 * the outcome it expected. A failure here is nearly always explained by a line of
	 * that log, and the test process cannot print it — so it is carried into the
	 * assertion instead.
	 */
	String describe() {
		return "the build exited with " + exitCode + " and said:" + System.lineSeparator() + output;
	}
}
