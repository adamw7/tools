package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.List;

import io.github.adamw7.tools.enforcer.text.FrontMatter;

/**
 * Shared {@code model} front matter check for the definition format rules. When a
 * whitelist of allowed model identifiers is configured and a definition declares
 * a {@code model} outside it, a violation is reported, so a typo such as
 * {@code claud-opus} cannot slip through. An unconfigured whitelist allows any
 * model.
 */
final class ModelAllowlist {

	private static final String MODEL_KEY = "model";

	private ModelAllowlist() {
	}

	/**
	 * @param allowedModels the configured whitelist, or {@code null} to allow any model
	 * @param label         how the definition is named in the message (e.g. {@code Command})
	 */
	static void collect(List<String> allowedModels, FrontMatter frontMatter, String label, File file,
			List<String> violations) {
		if (allowedModels == null) {
			return;
		}
		frontMatter.value(MODEL_KEY).ifPresent(model -> addViolation(allowedModels, model, label, file, violations));
	}

	private static void addViolation(List<String> allowedModels, String model, String label, File file,
			List<String> violations) {
		if (!allowedModels.contains(model)) {
			violations.add(label + " declares unsupported model '" + model + "' in: " + file);
		}
	}
}
