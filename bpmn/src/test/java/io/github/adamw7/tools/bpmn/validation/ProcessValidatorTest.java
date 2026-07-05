package io.github.adamw7.tools.bpmn.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.bpmn.model.FlowNode;
import io.github.adamw7.tools.bpmn.model.FlowNodeType;
import io.github.adamw7.tools.bpmn.model.Process;
import io.github.adamw7.tools.bpmn.model.SequenceFlow;

class ProcessValidatorTest {

	private final ProcessValidator validator = new ProcessValidator();

	private FlowNode node(String id, FlowNodeType type) {
		return new FlowNode(id, "", type);
	}

	private SequenceFlow flow(String id, String source, String target) {
		return new SequenceFlow(id, "", source, target);
	}

	private Process straightLine() {
		return new Process("p", "", List.of(
				node("s", FlowNodeType.START_EVENT),
				node("t", FlowNodeType.TASK),
				node("e", FlowNodeType.END_EVENT)),
				List.of(flow("f1", "s", "t"), flow("f2", "t", "e")));
	}

	private boolean hasError(List<ValidationIssue> issues, String fragment) {
		return issues.stream().anyMatch(issue -> issue.isError() && issue.message().contains(fragment));
	}

	private boolean hasWarning(List<ValidationIssue> issues, String fragment) {
		return issues.stream()
				.anyMatch(issue -> issue.severity() == Severity.WARNING && issue.message().contains(fragment));
	}

	@Test
	void wellFormedProcessHasNoIssues() {
		assertTrue(validator.validate(straightLine()).isEmpty());
		assertTrue(validator.isValid(straightLine()));
	}

	@Test
	void flagsMissingStartEvent() {
		Process process = new Process("p", "", List.of(node("e", FlowNodeType.END_EVENT)), List.of());

		assertTrue(hasError(validator.validate(process), "no start event"));
	}

	@Test
	void flagsMissingEndEvent() {
		Process process = new Process("p", "", List.of(node("s", FlowNodeType.START_EVENT)), List.of());

		assertTrue(hasError(validator.validate(process), "no end event"));
	}

	@Test
	void flagsBlankProcessId() {
		Process process = new Process("", "", List.of(
				node("s", FlowNodeType.START_EVENT), node("e", FlowNodeType.END_EVENT)),
				List.of(flow("f", "s", "e")));

		assertTrue(hasError(validator.validate(process), "no id"));
		assertFalse(validator.isValid(process));
	}

	@Test
	void flagsSequenceFlowWithUnknownTarget() {
		Process process = new Process("p", "", List.of(
				node("s", FlowNodeType.START_EVENT), node("e", FlowNodeType.END_EVENT)),
				List.of(flow("f", "s", "ghost")));

		assertTrue(hasError(validator.validate(process), "targetRef 'ghost'"));
	}

	@Test
	void warnsAboutUnreachableNode() {
		Process process = new Process("p", "", List.of(
				node("s", FlowNodeType.START_EVENT),
				node("island", FlowNodeType.TASK),
				node("e", FlowNodeType.END_EVENT)),
				List.of(flow("f", "s", "e")));

		List<ValidationIssue> issues = validator.validate(process);
		assertTrue(hasWarning(issues, "unreachable"));
		assertTrue(hasWarning(issues, "dead end"));
		assertTrue(validator.isValid(process));
	}

	@Test
	void startEventNeedsNoIncomingAndEndEventNeedsNoOutgoing() {
		List<ValidationIssue> issues = validator.validate(straightLine());

		assertFalse(hasWarning(issues, "unreachable"));
		assertFalse(hasWarning(issues, "dead end"));
	}

	@Test
	void reportsSeverityCountsForACompletelyBrokenProcess() {
		Process process = new Process("", "", List.of(node("t", FlowNodeType.TASK)),
				List.of(flow("f", "t", "missing")));

		List<ValidationIssue> issues = validator.validate(process);

		long errors = issues.stream().filter(ValidationIssue::isError).count();
		assertEquals(4, errors, "blank id, no start, no end, plus dangling target");
	}
}
