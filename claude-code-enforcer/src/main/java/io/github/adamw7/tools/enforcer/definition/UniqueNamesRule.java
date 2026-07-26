package io.github.adamw7.tools.enforcer.definition;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that fails the build when two Claude Code definitions claim the
 * same name. A command's and a sub-agent's name is its {@code *.md} file name and
 * a skill's name is its directory name, so a command and a sub-agent both called
 * {@code review}, or two skills called {@code commit}, are a real source of
 * confusion.
 * <p>
 * Names are gathered from every configured directory and checked across all of
 * them at once, so a clash between a command and a skill is caught just like one
 * between two commands. Each name used more than once is reported together with
 * every file or directory that uses it.
 */
@Named("uniqueNames")
public class UniqueNamesRule extends MultiDefinitionRule {

	@Override
	public void execute() throws EnforcerRuleException {
		verifyConfigured();
		Duplicates names = new Duplicates();
		forEachDefinition((definitionFile, source, name) -> names.add(name, name, source.toString()));
		report("Claude Code names must be unique:", names.violations("name"));
	}
}
