package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.protos.Person;

public class MessagesFinderTest {

	@Test
	public void happyPath() {
		Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder("io.github.adamw7.tools.code.protos").execute();
		assertTrue(allMessages.contains(Person.class));
	}
}
