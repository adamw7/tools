package io.github.adamw7.context.mcp;

import java.util.Map;

import io.github.adamw7.context.Language;

/**
 * Resolves the optional {@code language} argument the context tools accept. The
 * generic argument parsing lives in the shared
 * {@link io.github.adamw7.tools.mcp.ToolArguments}; language resolution stays here
 * because it depends on the context module's {@link Language} type.
 */
final class LanguageArguments {

	private LanguageArguments() {
	}

	static Language optionalLanguage(Map<String, Object> arguments, String key, Language defaultLanguage) {
		Object value = arguments.get(key);
		return value == null ? defaultLanguage : Language.fromName(String.valueOf(value));
	}
}
