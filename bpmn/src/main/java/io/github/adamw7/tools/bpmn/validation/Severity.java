package io.github.adamw7.tools.bpmn.validation;

/** How serious a {@link ValidationIssue} is. */
public enum Severity {

	/** A structural defect that makes the process invalid. */
	ERROR,

	/** A suspicious but tolerable trait, such as an unreachable node. */
	WARNING
}
