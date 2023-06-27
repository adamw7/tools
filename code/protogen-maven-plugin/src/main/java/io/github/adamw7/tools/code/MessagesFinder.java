package io.github.adamw7.tools.code;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import com.google.protobuf.GeneratedMessageV3;

public class MessagesFinder {
	
	private final static Logger log = LogManager.getLogger(MessagesFinder.class.getName());
	private final String[] pkg;

	public MessagesFinder(String... pkg) {
		this.pkg = pkg;
	}
	
	public Set<Class<? extends GeneratedMessageV3>> execute() {
		 Reflections reflections =
			        new Reflections(new ConfigurationBuilder().forPackages(pkg));
		Set<Class<? extends GeneratedMessageV3>> classes = reflections.getSubTypesOf(GeneratedMessageV3.class);
		log.info("Found these proto classes:");
		for (Class<? extends GeneratedMessageV3> cl : classes) {
			log.info(cl);
		}
		return classes;
	}

}
