package io.github.adamw7.tools.bpmn.parse;

/**
 * Thrown when a BPMN document cannot be read into a model, for example because
 * the XML is malformed or its root element is not {@code definitions}.
 */
public class BpmnParseException extends RuntimeException {

	public BpmnParseException(String message) {
		super(message);
	}

	public BpmnParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
