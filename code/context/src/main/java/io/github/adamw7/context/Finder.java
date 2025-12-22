package io.github.adamw7.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Finder implements Context {
	private static final Logger log = LogManager.getLogger(Finder.class.getName());
	private final Set<ClassContainer> allContainers;

	public Finder(Set<ClassContainer> allContainers) {
		this.allContainers = allContainers;
	}

	@Override
	public Set<ClassContainer> find(ClassContainer root, int depth) {
		Set<ClassContainer> classes = findAllUsedClasses(root, depth);
		for (int i = depth - 1; i > 0; i--) {
			for (ClassContainer clazz : classes) {
				classes.addAll(find(clazz, i));
			}
		}
		return classes;
	}

	private Set<ClassContainer> findAllUsedClasses(ClassContainer root, int depth) {
		if (depth <= 0) {
			throw new IllegalArgumentException("Depth must be positive and received: " + depth);
		}
		Pattern pattern = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(\\.[A-Z][A-Za-z0-9_]*)*\\b");
		return findByRegEx(root, pattern);
	}

	private Set<ClassContainer> findByRegEx(ClassContainer root, Pattern pattern) {
		Set<ClassContainer> classes = ConcurrentHashMap.newKeySet();
		Matcher matcher = pattern.matcher(root.originalCode());
		while (matcher.find()) {
			String className = matcher.group();
			ClassContainer container = findContainer(className);
			if (container != null) {
				log.info("Found {} used in {}", container.className(), root.className());
				classes.add(container);				
			}
		}
		return classes;
	}

	private ClassContainer findContainer(String className) {
		for (ClassContainer container : allContainers) {
			if (container.className().equals(className + ".java")) {
				return container;
			}
		}
		return null;
	}
}
