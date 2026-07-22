package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.ProxySelector;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * Verifies that {@link NetworkOff} both wires up {@link NetworkOffExtension} and,
 * once applied, leaves the annotated class running with the network off. The
 * class annotates itself, so the behavioural assertion exercises the real path a
 * developer would take.
 */
@NetworkOff
public class NetworkOffAnnotationTest {

	@Test
	public void annotatedClassRunsWithNetworkOff() {
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> ProxySelector.getDefault().select(URI.create("http://192.0.2.1")),
				"@NetworkOff should have engaged the kill-switch before the test ran");

		assertEquals("The network is off", thrown.getMessage());
	}

	@Test
	public void annotationRegistersTheKillSwitchExtension() {
		ExtendWith extendWith = NetworkOff.class.getAnnotation(ExtendWith.class);

		assertTrue(extendWith != null && extendWith.value().length == 1
				&& extendWith.value()[0].equals(NetworkOffExtension.class),
				"@NetworkOff must register NetworkOffExtension via @ExtendWith");
	}

	@Test
	public void annotationIsDiscoverableAtRuntimeOnTypes() {
		Retention retention = NetworkOff.class.getAnnotation(Retention.class);
		Target target = NetworkOff.class.getAnnotation(Target.class);

		assertEquals(RetentionPolicy.RUNTIME, retention.value(),
				"@NetworkOff must be retained at runtime for JUnit to read it");
		assertEquals(ElementType.TYPE, target.value()[0], "@NetworkOff applies to test classes");
		assertTrue(AnnotationSupport.isAnnotated(NetworkOffAnnotationTest.class, NetworkOff.class),
				"the annotation must be discoverable on the class that carries it");
	}
}
