package io.github.adamw7.tools.enforcer.doc;

/**
 * A read-only {@link CharSequence} view that counts every character the regular
 * expression engine reads and aborts once a step budget is exceeded. A
 * catastrophically backtracking pattern reads characters an exponential number
 * of times, so wrapping the match input in this sequence turns a build-hanging
 * ReDoS into a prompt {@link BacktrackLimitExceededException} that the caller can
 * report as a violation instead of letting the build thread spin.
 * <p>
 * The step counter is shared with every {@link #subSequence} view, so group
 * extraction cannot reset the budget. {@link #toString} does not count, so a
 * captured group's text can be materialised without spending the budget.
 */
final class BoundedCharSequence implements CharSequence {

	/** Signals that a match read more characters than its budget allowed. */
	static final class BacktrackLimitExceededException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}

	private final CharSequence content;
	private final long maxSteps;
	private final long[] steps;

	BoundedCharSequence(CharSequence content, long maxSteps) {
		this(content, maxSteps, new long[1]);
	}

	private BoundedCharSequence(CharSequence content, long maxSteps, long[] steps) {
		this.content = content;
		this.maxSteps = maxSteps;
		this.steps = steps;
	}

	@Override
	public int length() {
		return content.length();
	}

	@Override
	public char charAt(int index) {
		if (++steps[0] > maxSteps) {
			throw new BacktrackLimitExceededException();
		}
		return content.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return new BoundedCharSequence(content.subSequence(start, end), maxSteps, steps);
	}

	@Override
	public String toString() {
		return content.toString();
	}
}
