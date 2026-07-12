package io.github.adamw7.tools.code;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import com.google.protobuf.GeneratedMessage;

public class MessagesFinder {

	private static final Logger log = LogManager.getLogger(MessagesFinder.class.getName());
	private final String[] pkg;

	public MessagesFinder(String... pkg) {
		this.pkg = pkg;
	}

	public Set<Class<? extends GeneratedMessage>> execute() {
		Reflections reflections = new Reflections(new ConfigurationBuilder().forPackages(pkg));
		Set<Class<? extends GeneratedMessage>> classes = reflections.getSubTypesOf(GeneratedMessage.class);
		Set<Class<? extends GeneratedMessage>> messages = onlyConcreteMessages(classes);
		log.info("Found {} concrete proto class(es): {}", messages::size, () -> messages);
		return messages;
	}

	private Set<Class<? extends GeneratedMessage>> onlyConcreteMessages(Set<Class<? extends GeneratedMessage>> classes) {
		return classes.stream().filter(this::isConcrete).collect(Collectors.toSet());
	}

	private boolean isConcrete(Class<? extends GeneratedMessage> clazz) {
		return !Modifier.isAbstract(clazz.getModifiers());
	}

}
