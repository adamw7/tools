package io.github.adamw7.tools.bpmn.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of parsing a BPMN 2.0 document: its target namespace and the
 * processes it defines. Immutable.
 */
public final class BpmnModel {

	private final String targetNamespace;
	private final List<Process> processes;

	public BpmnModel(String targetNamespace, List<Process> processes) {
		this.targetNamespace = Objects.requireNonNull(targetNamespace, "targetNamespace");
		this.processes = List.copyOf(processes);
	}

	/** The document's {@code targetNamespace}, or empty when it declares none. */
	public String targetNamespace() {
		return targetNamespace;
	}

	public List<Process> processes() {
		return processes;
	}

	/** The process carrying the given id, if any. */
	public Optional<Process> findProcess(String processId) {
		Objects.requireNonNull(processId, "processId");
		return processes.stream().filter(process -> process.id().equals(processId)).findFirst();
	}
}
