package io.github.adamw7.tools.enforcer.definition;

import javax.inject.Named;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Enforcer rule that fails the build when two Claude Code definitions claim the
 * same name. A command's name is its {@code *.md} file name, a sub-agent's name
 * is its {@code *.md} file name, and a skill's name is its directory name, so a
 * command and a sub-agent both called {@code review}, or two skills called
 * {@code commit}, are a real source of confusion. The rule gathers the names
 * from every configured directory and reports each name that is used more than
 * once, naming every file or directory that uses it.
 * <p>
 * Uniqueness is checked across every configured directory at once, so a clash
 * between a command and a skill is caught just like a clash between two commands.
 * All clashes found are reported together.
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
