package io.github.adamw7.tools.code;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import com.google.protobuf.GeneratedMessage;

public class MessagesFinder {

	private final Log log;
	private final String[] pkg;

	public MessagesFinder(Log log, String... pkg) {
		this.log = log;
		this.pkg = pkg;
	}

	public Set<Class<? extends GeneratedMessage>> execute() {
		Reflections reflections = new Reflections(new ConfigurationBuilder().forPackages(pkg));
		Set<Class<? extends GeneratedMessage>> classes = reflections.getSubTypesOf(GeneratedMessage.class);
		Set<Class<? extends GeneratedMessage>> messages = onlyConcreteMessages(classes);
		log.info("Found " + messages.size() + " concrete proto class(es): " + messages);
		return messages;
	}

	private Set<Class<? extends GeneratedMessage>> onlyConcreteMessages(Set<Class<? extends GeneratedMessage>> classes) {
		return classes.stream().filter(this::isConcrete).collect(Collectors.toSet());
	}

	private boolean isConcrete(Class<? extends GeneratedMessage> clazz) {
		return !Modifier.isAbstract(clazz.getModifiers());
	}

}
