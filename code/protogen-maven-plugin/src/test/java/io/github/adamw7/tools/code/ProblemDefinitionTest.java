package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.protobuf.UninitializedMessageException;

import io.github.adamw7.tools.code.protos.Person;

public class ProblemDefinitionTest {

	@Test
	public void demonstrateRequiredFieldIssue() {
		Person.Builder personBuilder = Person.newBuilder();

		personBuilder.setEmail("email@sth.com");
		personBuilder.setName("Adam");

		UninitializedMessageException thrown = assertThrows(UninitializedMessageException.class, () -> {
			personBuilder.build();
		}, "Expected build method to throw, but it didn't");

		assertEquals("Message missing required fields: id, department", thrown.getMessage());

	}
}
