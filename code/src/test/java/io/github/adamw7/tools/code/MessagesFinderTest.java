package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.protobuf.GeneratedMessageV3;

import io.github.adamw7.tools.code.protos.AddressBook;
import io.github.adamw7.tools.code.protos.Person;
import io.github.adamw7.tools.code.protos.Person.PhoneNumber;

public class MessagesFinderTest {

	@Test
	public void happyPath() {
		Set<Class<? extends GeneratedMessageV3>> allMessages = new MessagesFinder().execute();
		assertTrue(allMessages.contains(Person.class));
		assertTrue(allMessages.contains(PhoneNumber.class));
		assertTrue(allMessages.contains(AddressBook.class));
	}
}
