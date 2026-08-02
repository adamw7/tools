package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;

import io.github.adamw7.tools.enforcer.text.FrontMatter;
import io.github.adamw7.tools.enforcer.text.NameConvention;

/**
 * The front matter checks the definition format rules share. A skill, a
 * sub-agent, and a command all declare their metadata the same way, so the
 * required-key, unknown-key, name, description, and model checks differ only in
 * the {@code label} naming the definition kind in the message — e.g.
 * {@code SKILL.md}, {@code Sub-agent}, or {@code Command} — and in which of them
 * a rule asks for.
 * <p>
 * Each check collects into the rule's own violation list, so a rule keeps
 * reporting every problem it finds in one grouped message. A {@code null}
 * whitelist means "allow anything", matching the rule configuration where the
 * check is simply not configured.
 */
final class FrontMatterChecks {

	private static final String NAME_KEY = "name";
	private static final String DESCRIPTION_KEY = "description";
	private static final String MODEL_KEY = "model";

	private final FrontMatter frontMatter;
	private final String label;
	private final File file;
	private final List<String> violations;

	FrontMatterChecks(FrontMatter frontMatter, String label, File file, List<String> violations) {
		this.frontMatter = frontMatter;
		this.label = label;
		this.file = file;
		this.violations = violations;
	}

	/** Reports every required key the front matter does not declare. */
	void requireKeys(List<String> requiredKeys) {
		for (String key : requiredKeys) {
			if (!frontMatter.hasKey(key)) {
				violations.add(label + " front matter is missing '" + key + ":' in: " + file);
			}
		}
	}

	/**
	 * Reports every key declared more than once. Only the last declaration takes
	 * effect, so an earlier one is a line the author wrote and Claude Code never
	 * reads — a second {@code description:} silently replaces the one above it.
	 */
	void rejectDuplicateKeys() {
		for (String key : frontMatter.duplicateKeys()) {
			violations.add(label + " front matter declares '" + key + ":' more than once in: " + file);
		}
	}

	/** Reports every declared key outside the whitelist, which catches a typo such as {@code descripton}. */
	void allowOnlyKeys(List<String> allowedKeys) {
		if (allowedKeys == null) {
			return;
		}
		for (String key : frontMatter.keys()) {
			if (!allowedKeys.contains(key)) {
				violations.add(label + " front matter has unknown key '" + key + ":' in: " + file);
			}
		}
	}

	/** Reports a declared {@code name} that breaks convention or does not match {@code expected}. */
	void checkName(String expected) {
		frontMatter.value(NAME_KEY)
				.ifPresent(name -> NameConvention.collect(name, expected, file.toString(), violations));
	}

	/**
	 * Reports a declared {@code description} that is blank, or longer than
	 * {@code maxLength} when a positive cap is given.
	 */
	void checkDescription(int maxLength) {
		frontMatter.value(DESCRIPTION_KEY).ifPresent(description -> addDescriptionViolation(description, maxLength));
	}

	private void addDescriptionViolation(String description, int maxLength) {
		if (description.isBlank()) {
			violations.add(label + " description must not be empty in: " + file);
		} else if (maxLength > 0 && description.length() > maxLength) {
			violations.add(label + " description exceeds " + maxLength + " characters in: " + file);
		}
	}

	/** Reports a declared {@code model} outside the whitelist, so a typo such as {@code claud-opus} cannot slip through. */
	void checkModel(List<String> allowedModels) {
		if (allowedModels == null) {
			return;
		}
		frontMatter.value(MODEL_KEY)
				.filter(model -> !allowedModels.contains(model))
				.ifPresent(model -> violations.add(label + " declares unsupported model '" + model + "' in: " + file));
	}
}
