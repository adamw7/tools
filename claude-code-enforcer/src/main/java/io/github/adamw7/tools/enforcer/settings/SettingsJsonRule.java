package io.github.adamw7.tools.enforcer.settings;

import java.io.File;

import io.github.adamw7.tools.enforcer.rule.JsonFileRule;

/**
 * Base for the rules that each check a section of {@code .claude/settings.json}:
 * the file parameter they all take, named once so the three cannot come to spell
 * it differently in a pom. Each subclass contributes only the section it reads and
 * the header it reports under.
 */
public abstract class SettingsJsonRule extends JsonFileRule {

	/** The {@code .claude/settings.json} file to validate. Injected from the rule configuration. */
	private File settingsFile;

	protected SettingsJsonRule() {
		super("settingsFile", "settings.json");
	}

	@Override
	protected final File jsonFile() {
		return settingsFile;
	}

	public void setSettingsFile(File settingsFile) {
		this.settingsFile = settingsFile;
	}
}
