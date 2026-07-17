package io.github.adamw7.tools.adopt;

/**
 * Unchecked failure raised when a step of the Claude Code adoption cannot
 * complete — a command returned a non-zero exit code, a process could not be
 * started, or a project file could not be edited.
 */
public class AdoptionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AdoptionException(String message) {
		super(message);
	}

	public AdoptionException(String message, Throwable cause) {
		super(message, cause);
	}
}
