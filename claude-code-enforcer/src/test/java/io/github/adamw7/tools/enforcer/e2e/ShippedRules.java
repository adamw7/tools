package io.github.adamw7.tools.enforcer.e2e;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Named;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import io.github.adamw7.tools.enforcer.rule.ClaudeCodeEnforcerRule;

/**
 * The rules this module ships, discovered from the compiled classes rather than
 * listed by hand, together with the configuration parameters each one exposes.
 * <p>
 * An end-to-end test that named its rules in a literal list would go on passing
 * after a new rule was added and never exercised, which is exactly the rule most
 * likely to be misconfigured. Deriving the catalogue from the classes turns that
 * silence into a failure: a rule, or a parameter, that no fixture configures is
 * reported by name.
 */
final class ShippedRules {

	private static final String ENFORCER_PACKAGE = "io.github.adamw7.tools.enforcer";
	private static final String SETTER_PREFIX = "set";

	private ShippedRules() {
	}

	/**
	 * Every concrete rule by the {@code @Named} name a pom configures it with, sorted
	 * so a failure reads the same way twice.
	 */
	static Map<String, Class<?>> byName() {
		Map<String, Class<?>> rules = new TreeMap<>();
		for (JavaClass candidate : importedClasses()) {
			addRule(candidate, rules);
		}
		if (rules.isEmpty()) {
			throw new IllegalStateException("No enforcer rules were found in " + ENFORCER_PACKAGE
					+ "; the compiled classes are not on the test class path");
		}
		return rules;
	}

	/**
	 * The parameters {@code rule} accepts: the setters it and its rule-specific bases
	 * declare, named as a pom names them. The walk stops at
	 * {@link ClaudeCodeEnforcerRule} because the parameters it declares — severity,
	 * the report file, the baseline — belong to every rule alike and are exercised on
	 * their own rather than once per rule.
	 */
	static Set<String> parametersOf(Class<?> rule) {
		Set<String> parameters = new LinkedHashSet<>();
		for (Class<?> type = rule; type != ClaudeCodeEnforcerRule.class; type = type.getSuperclass()) {
			addParameters(type, parameters);
		}
		return parameters;
	}

	private static void addRule(JavaClass candidate, Map<String, Class<?>> rules) {
		if (isConcreteRule(candidate)) {
			Class<?> rule = candidate.reflect();
			rules.put(rule.getAnnotation(Named.class).value(), rule);
		}
	}

	private static boolean isConcreteRule(JavaClass candidate) {
		return candidate.isAssignableTo(ClaudeCodeEnforcerRule.class)
				&& !candidate.getModifiers().contains(JavaModifier.ABSTRACT)
				&& candidate.isAnnotatedWith(Named.class);
	}

	private static void addParameters(Class<?> type, Set<String> parameters) {
		for (Method method : type.getDeclaredMethods()) {
			addParameter(method, parameters);
		}
	}

	private static void addParameter(Method method, Set<String> parameters) {
		if (isSetter(method)) {
			parameters.add(uncapitalized(method.getName().substring(SETTER_PREFIX.length())));
		}
	}

	private static boolean isSetter(Method method) {
		return method.getName().startsWith(SETTER_PREFIX)
				&& method.getParameterCount() == 1
				&& !Modifier.isStatic(method.getModifiers())
				&& !method.isSynthetic();
	}

	private static String uncapitalized(String name) {
		return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
	}

	/**
	 * Production classes only: the test classes on the same class path include this
	 * package's own helpers, and importing them would only slow the scan down.
	 */
	private static Iterable<JavaClass> importedClasses() {
		return new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages(ENFORCER_PACKAGE);
	}
}
