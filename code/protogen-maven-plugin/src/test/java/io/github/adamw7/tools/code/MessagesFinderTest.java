package io.github.adamw7.tools.code;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Modifier;
import java.util.Set;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.protos.Account;
import io.github.adamw7.tools.code.protos.Person;

public class MessagesFinderTest {

	private static final String PROTOS_PKG = "io.github.adamw7.tools.code.protos";

	private final Log log = mock(Log.class);

	@Test
	public void happyPath() {
		Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder(log, PROTOS_PKG).execute();
		assertTrue(allMessages.contains(Person.class));
	}

	@Test
	public void findsExtendableMessage() {
		Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder(log, PROTOS_PKG).execute();
		assertTrue(allMessages.contains(Account.class));
	}

	@Test
	public void skipsAbstractFrameworkClasses() {
		Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder(log, PROTOS_PKG).execute();
		for (Class<? extends GeneratedMessage> message : allMessages) {
			assertFalse(Modifier.isAbstract(message.getModifiers()), message + " should be concrete");
		}
	}

	/**
	 * The scan reports what it found through the Mojo log the goal handed it, so
	 * the line honours {@code -q} and {@code -X} and is attributed to the plugin
	 * in the reactor output — which logging through log4j2 from inside a Maven
	 * plugin does not.
	 */
	@Test
	public void reportsWhatItFoundThroughTheMojoLog() {
		new MessagesFinder(log, PROTOS_PKG).execute();

		verify(log).info(contains("concrete proto class(es)"));
	}
}
