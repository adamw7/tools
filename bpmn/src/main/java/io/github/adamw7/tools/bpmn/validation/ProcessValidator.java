package io.github.adamw7.tools.bpmn.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.adamw7.tools.bpmn.model.FlowNode;
import io.github.adamw7.tools.bpmn.model.FlowNodeType;
import io.github.adamw7.tools.bpmn.model.Process;
import io.github.adamw7.tools.bpmn.model.SequenceFlow;

/**
 * Checks a parsed {@link Process} for common structural defects: a missing
 * start or end event, sequence flows that reference unknown nodes, and nodes
 * that are unreachable or lead nowhere.
 *
 * <p>Errors mean the process is structurally invalid; warnings flag suspicious
 * but tolerable traits. The order of the returned issues is not significant.
 */
public final class ProcessValidator {

	/** Runs every rule and returns all issues found, most severe rules first. */
	public List<ValidationIssue> validate(Process process) {
		Objects.requireNonNull(process, "process");
		List<ValidationIssue> issues = new ArrayList<>();
		checkProcessId(process, issues);
		checkHasEvent(process, FlowNodeType.START_EVENT, "start", issues);
		checkHasEvent(process, FlowNodeType.END_EVENT, "end", issues);
		checkFlowReferences(process, issues);
		checkConnectivity(process, issues);
		return issues;
	}

	/** True when the process has no {@link Severity#ERROR} issues. */
	public boolean isValid(Process process) {
		return validate(process).stream().noneMatch(ValidationIssue::isError);
	}

	private void checkProcessId(Process process, List<ValidationIssue> issues) {
		if (process.id().isBlank()) {
			issues.add(ValidationIssue.error("", "Process has no id"));
		}
	}

	private void checkHasEvent(Process process, FlowNodeType type, String label, List<ValidationIssue> issues) {
		if (process.nodesOfType(type).isEmpty()) {
			issues.add(ValidationIssue.error(process.id(), "Process has no " + label + " event"));
		}
	}

	private void checkFlowReferences(Process process, List<ValidationIssue> issues) {
		process.sequenceFlows().forEach(flow -> checkFlowEnds(process, flow, issues));
	}

	private void checkFlowEnds(Process process, SequenceFlow flow, List<ValidationIssue> issues) {
		checkReference(process, flow.id(), flow.sourceRef(), "source", issues);
		checkReference(process, flow.id(), flow.targetRef(), "target", issues);
	}

	private void checkReference(Process process, String flowId, String nodeId, String end,
			List<ValidationIssue> issues) {
		if (process.findNode(nodeId).isEmpty()) {
			issues.add(ValidationIssue.error(flowId,
					"Sequence flow " + end + "Ref '" + nodeId + "' does not match any node"));
		}
	}

	private void checkConnectivity(Process process, List<ValidationIssue> issues) {
		process.flowNodes().forEach(node -> checkNodeConnectivity(process, node, issues));
	}

	private void checkNodeConnectivity(Process process, FlowNode node, List<ValidationIssue> issues) {
		if (needsIncoming(node) && process.incoming(node.id()).isEmpty()) {
			issues.add(ValidationIssue.warning(node.id(), "Node '" + node.id() + "' is unreachable (no incoming flow)"));
		}
		if (needsOutgoing(node) && process.outgoing(node.id()).isEmpty()) {
			issues.add(ValidationIssue.warning(node.id(), "Node '" + node.id() + "' is a dead end (no outgoing flow)"));
		}
	}

	private boolean needsIncoming(FlowNode node) {
		return node.type() != FlowNodeType.START_EVENT && node.type() != FlowNodeType.BOUNDARY_EVENT;
	}

	private boolean needsOutgoing(FlowNode node) {
		return node.type() != FlowNodeType.END_EVENT;
	}
}
