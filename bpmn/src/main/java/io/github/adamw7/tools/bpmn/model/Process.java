package io.github.adamw7.tools.bpmn.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single BPMN process: its flow nodes and the sequence flows connecting them.
 * Immutable; the collections handed in are defensively copied.
 */
public final class Process {

	private final String id;
	private final String name;
	private final List<FlowNode> flowNodes;
	private final List<SequenceFlow> sequenceFlows;

	public Process(String id, String name, List<FlowNode> flowNodes, List<SequenceFlow> sequenceFlows) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.flowNodes = List.copyOf(flowNodes);
		this.sequenceFlows = List.copyOf(sequenceFlows);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public List<FlowNode> flowNodes() {
		return flowNodes;
	}

	public List<SequenceFlow> sequenceFlows() {
		return sequenceFlows;
	}

	/** All flow nodes of the given type, in document order. */
	public List<FlowNode> nodesOfType(FlowNodeType type) {
		Objects.requireNonNull(type, "type");
		return flowNodes.stream().filter(node -> node.type() == type).toList();
	}

	/** The flow node carrying the given id, if any. */
	public Optional<FlowNode> findNode(String nodeId) {
		Objects.requireNonNull(nodeId, "nodeId");
		return flowNodes.stream().filter(node -> node.id().equals(nodeId)).findFirst();
	}

	/** Sequence flows leaving the node with the given id. */
	public List<SequenceFlow> outgoing(String nodeId) {
		Objects.requireNonNull(nodeId, "nodeId");
		return sequenceFlows.stream().filter(flow -> flow.sourceRef().equals(nodeId)).toList();
	}

	/** Sequence flows entering the node with the given id. */
	public List<SequenceFlow> incoming(String nodeId) {
		Objects.requireNonNull(nodeId, "nodeId");
		return sequenceFlows.stream().filter(flow -> flow.targetRef().equals(nodeId)).toList();
	}
}
