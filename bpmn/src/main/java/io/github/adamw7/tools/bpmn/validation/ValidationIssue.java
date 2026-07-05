package io.github.adamw7.tools.bpmn.validation;

import java.util.Objects;

/**
 * A single problem found while validating a process. Immutable.
 *
 * @param severity  how serious the problem is
 * @param elementId the id of the offending element, or empty when the issue is
 *                  about the process as a whole
 * @param message   a human-readable description of the problem
 */
public record ValidationIssue(Severity severity, String elementId, String message) {

	public ValidationIssue {
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(elementId, "elementId");
		Objects.requireNonNull(message, "message");
	}

	public static ValidationIssue error(String elementId, String message) {
		return new ValidationIssue(Severity.ERROR, elementId, message);
	}

	public static ValidationIssue warning(String elementId, String message) {
		return new ValidationIssue(Severity.WARNING, elementId, message);
	}

	public boolean isError() {
		return severity == Severity.ERROR;
	}
}
