package io.github.adamw7.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Finder implements Context {

	private static final Logger log = LogManager.getLogger(Finder.class.getName());
	private static final Pattern CLASS_NAME_PATTERN =
			Pattern.compile("\\b[A-Z][A-Za-z0-9_]*(\\.[A-Z][A-Za-z0-9_]*)*\\b");

	private final Set<ClassContainer> allContainers;

	public Finder(Set<ClassContainer> allContainers) {
		this.allContainers = allContainers;
	}

	@Override
	public Set<ClassContainer> find(ClassContainer root, int depth) {
		if (depth <= 0) {
			throw new IllegalArgumentException("Depth must be positive and received: " + depth);
		}
		Set<ClassContainer> visited = new HashSet<>();
		visited.add(root);
		Set<ClassContainer> frontier = new HashSet<>();
		frontier.add(root);
		for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
			frontier = expand(frontier, visited);
		}
		return visited;
	}

	private Set<ClassContainer> expand(Set<ClassContainer> frontier, Set<ClassContainer> visited) {
		Set<ClassContainer> next = new HashSet<>();
		for (ClassContainer current : frontier) {
			for (ClassContainer referenced : referencesIn(current)) {
				if (visited.add(referenced)) {
					next.add(referenced);
				}
			}
		}
		return next;
	}

	private Set<ClassContainer> referencesIn(ClassContainer source) {
		Set<ClassContainer> references = new HashSet<>();
		Matcher matcher = CLASS_NAME_PATTERN.matcher(source.originalCode());
		while (matcher.find()) {
			ClassContainer container = findContainer(matcher.group());
			if (container != null) {
				log.info("Found {} used in {}", container.className(), source.className());
				references.add(container);
			}
		}
		return references;
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
