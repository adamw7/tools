package io.github.adamw7.tools.enforcer.definition;

import java.io.File;
import java.util.Optional;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;

import io.github.adamw7.tools.enforcer.text.FrontMatterFixer;
import io.github.adamw7.tools.enforcer.text.MarkdownText;

/**
 * Bridges {@link FrontMatterFixer} to the definition rules that read a Markdown
 * file before validating its front matter. When auto-fix is enabled and the
 * content has a repairable front matter block, it rewrites the file on disk,
 * logs what it did, and hands the rule the repaired content so the rest of the
 * checks run against the corrected document instead of failing the build.
 */
final class FrontMatterAutoFix {

	private FrontMatterAutoFix() {
	}

	/**
	 * Returns {@code content} unchanged when auto-fix is off or there is nothing
	 * to repair; otherwise writes and returns the repaired content.
	 */
	static String apply(File file, String description, String content, boolean autoFix, EnforcerLogger log) {
		if (!autoFix) {
			return content;
		}
		Optional<String> repaired = FrontMatterFixer.repair(content);
		if (repaired.isEmpty()) {
			return content;
		}
		MarkdownText.write(file, repaired.get(), description);
		log.info("Auto-fixed malformed front matter in " + file);
		return repaired.get();
	}
}
