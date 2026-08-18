package io.github.adamw7.tools.enforcer.e2e;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * A {@code <rules>} block as a pom writes one, and what it says about itself.
 * <p>
 * {@link #complete()} configures every rule this module ships with every parameter
 * that rule accepts — the whole surface a project can address the enforcer through.
 * Maven refuses an element it cannot bind ("Cannot find 'x' in class …"), so a
 * build that gets through this configuration has proved that each
 * {@code @Named} name resolves to a rule and each element resolves to a field. The
 * block reads itself back through the same XML it hands Maven, which is what lets
 * a test compare it against the shipped rules without a second, hand-kept list to
 * drift.
 */
final class RuleConfiguration {

	private static final String COMPLETE = """
								<claudeMdFormat>
									<claudeMdFile>${project.basedir}/CLAUDE.md</claudeMdFile>
									<titleHeading># CLAUDE.md</titleHeading>
									<requiredReference>AGENTS.md</requiredReference>
									<requiredSections>
										<requiredSection>## Project</requiredSection>
										<requiredSection>## Java version</requiredSection>
										<requiredSection>## Maven</requiredSection>
										<requiredSection>## Principles for Java Development</requiredSection>
										<requiredSection>## Testing</requiredSection>
										<requiredSection>## Dependencies</requiredSection>
									</requiredSections>
									<forbiddenTokens>
										<forbiddenToken>TODO</forbiddenToken>
									</forbiddenTokens>
									<enforceSectionOrder>true</enforceSectionOrder>
									<maxLineLength>100</maxLineLength>
									<validateFileReferences>true</validateFileReferences>
									<referenceBaseDir>${project.basedir}</referenceBaseDir>
								</claudeMdFormat>
								<agentsMdFormat>
									<agentsMdFile>${project.basedir}/AGENTS.md</agentsMdFile>
									<titleHeading># AGENTS.md</titleHeading>
									<requiredSections>
										<requiredSection>## Project overview</requiredSection>
										<requiredSection>## Module layout</requiredSection>
										<requiredSection>## Environment &amp; toolchain</requiredSection>
										<requiredSection>## Build, test, and run</requiredSection>
										<requiredSection>## Code style &amp; conventions</requiredSection>
										<requiredSection>## Releasing</requiredSection>
										<requiredSection>## Pull requests &amp; commits</requiredSection>
									</requiredSections>
									<forbiddenTokens>
										<forbiddenToken>TODO</forbiddenToken>
									</forbiddenTokens>
									<enforceSectionOrder>true</enforceSectionOrder>
									<maxLineLength>100</maxLineLength>
									<validateFileReferences>true</validateFileReferences>
									<referenceBaseDir>${project.basedir}</referenceBaseDir>
								</agentsMdFormat>
								<crossDocConsistency>
									<claudeMdFile>${project.basedir}/CLAUDE.md</claudeMdFile>
									<agentsMdFile>${project.basedir}/AGENTS.md</agentsMdFile>
									<consistentPatterns>
										<consistentPattern>Java (\\d+)</consistentPattern>
									</consistentPatterns>
								</crossDocConsistency>
								<readmeConsistency>
									<readmeFile>${project.basedir}/README.md</readmeFile>
									<agentDocFile>${project.basedir}/AGENTS.md</agentDocFile>
									<consistentPatterns>
										<consistentPattern>proto(\\d)</consistentPattern>
									</consistentPatterns>
								</readmeConsistency>
								<memoryImports>
									<claudeMdFile>${project.basedir}/CLAUDE.md</claudeMdFile>
									<maxDepth>3</maxDepth>
									<ignoredImports>
										<ignoredImport>ignored/never-loaded.md</ignoredImport>
									</ignoredImports>
									<importExtensions>
										<importExtension>md</importExtension>
										<importExtension>markdown</importExtension>
										<importExtension>txt</importExtension>
									</importExtensions>
								</memoryImports>
								<moduleMapConsistency>
									<pomFile>${project.basedir}/reactor-pom.xml</pomFile>
									<docFiles>
										<docFile>${project.basedir}/CLAUDE.md</docFile>
										<docFile>${project.basedir}/AGENTS.md</docFile>
									</docFiles>
									<ignoredModules>
										<ignoredModule>gamma</ignoredModule>
									</ignoredModules>
								</moduleMapConsistency>
								<contextBudget>
									<files>
										<file>${project.basedir}/CLAUDE.md</file>
									</files>
									<directories>
										<directory>${project.basedir}/.claude/skills</directory>
									</directories>
									<maxBytes>32768</maxBytes>
									<maxLines>500</maxLines>
									<maxTokens>8192</maxTokens>
								</contextBudget>
								<skillFilesExist>
									<skillsDir>${project.basedir}/.claude/skills</skillsDir>
									<requiredKeys>
										<requiredKey>name</requiredKey>
										<requiredKey>description</requiredKey>
									</requiredKeys>
									<allowedFrontMatterKeys>
										<allowedFrontMatterKey>name</allowedFrontMatterKey>
										<allowedFrontMatterKey>description</allowedFrontMatterKey>
									</allowedFrontMatterKeys>
									<maxDescriptionLength>200</maxDescriptionLength>
									<autoFix>false</autoFix>
								</skillFilesExist>
								<subAgentFormat>
									<agentsDir>${project.basedir}/.claude/agents</agentsDir>
									<requiredKeys>
										<requiredKey>name</requiredKey>
										<requiredKey>description</requiredKey>
									</requiredKeys>
									<allowedModels>
										<allowedModel>sonnet</allowedModel>
										<allowedModel>opus</allowedModel>
									</allowedModels>
									<autoFix>false</autoFix>
								</subAgentFormat>
								<commandFormat>
									<commandsDir>${project.basedir}/.claude/commands</commandsDir>
									<allowedFrontMatterKeys>
										<allowedFrontMatterKey>description</allowedFrontMatterKey>
										<allowedFrontMatterKey>model</allowedFrontMatterKey>
									</allowedFrontMatterKeys>
									<allowedModels>
										<allowedModel>sonnet</allowedModel>
										<allowedModel>opus</allowedModel>
									</allowedModels>
									<autoFix>false</autoFix>
								</commandFormat>
								<uniqueNames>
									<commandsDir>${project.basedir}/.claude/commands</commandsDir>
									<agentsDir>${project.basedir}/.claude/agents</agentsDir>
									<skillsDir>${project.basedir}/.claude/skills</skillsDir>
								</uniqueNames>
								<uniqueDescriptions>
									<commandsDir>${project.basedir}/.claude/commands</commandsDir>
									<agentsDir>${project.basedir}/.claude/agents</agentsDir>
									<skillsDir>${project.basedir}/.claude/skills</skillsDir>
								</uniqueDescriptions>
								<pluginFormat>
									<pluginFile>${project.basedir}/.claude-plugin/plugin.json</pluginFile>
									<requiredKeys>
										<requiredKey>name</requiredKey>
									</requiredKeys>
									<allowedKeys>
										<allowedKey>name</allowedKey>
										<allowedKey>version</allowedKey>
										<allowedKey>description</allowedKey>
									</allowedKeys>
								</pluginFormat>
								<settingsJsonValid>
									<settingsFile>${project.basedir}/.claude/settings.json</settingsFile>
									<requiredPermissions>
										<requiredPermission>Bash(mvn *)</requiredPermission>
									</requiredPermissions>
									<forbiddenPermissions>
										<forbiddenPermission>Bash(*)</forbiddenPermission>
									</forbiddenPermissions>
								</settingsJsonValid>
								<permissionsFormat>
									<settingsFile>${project.basedir}/.claude/settings.json</settingsFile>
									<allowedTools>
										<allowedTool>Bash</allowedTool>
										<allowedTool>Read</allowedTool>
									</allowedTools>
									<forbiddenEntryPatterns>
										<forbiddenEntryPattern>Bash\\(\\*\\)</forbiddenEntryPattern>
									</forbiddenEntryPatterns>
								</permissionsFormat>
								<hookCommandsValid>
									<settingsFile>${project.basedir}/.claude/settings.json</settingsFile>
									<projectDir>${project.basedir}</projectDir>
									<allowedEvents>
										<allowedEvent>SessionStart</allowedEvent>
									</allowedEvents>
									<validateScriptReferences>true</validateScriptReferences>
								</hookCommandsValid>
								<hooksFormat>
									<hooksDir>${project.basedir}/.claude/hooks</hooksDir>
									<settingsFile>${project.basedir}/.claude/settings.json</settingsFile>
									<projectDir>${project.basedir}</projectDir>
									<allowedExtensions>
										<allowedExtension>sh</allowedExtension>
									</allowedExtensions>
									<requireShebang>true</requireShebang>
									<requireExecutable>true</requireExecutable>
									<reportUnreferencedScripts>true</reportUnreferencedScripts>
								</hooksFormat>
								<localSettingsIgnored>
									<gitignoreFile>${project.basedir}/.gitignore</gitignoreFile>
									<ignoredPaths>
										<ignoredPath>.claude/settings.local.json</ignoredPath>
									</ignoredPaths>
								</localSettingsIgnored>
								<noSecrets>
									<files>
										<file>${project.basedir}/.claude/settings.json</file>
										<file>${project.basedir}/.mcp.json</file>
									</files>
									<directories>
										<directory>${project.basedir}/.claude/hooks</directory>
									</directories>
									<secretPatterns>
										<secretPattern>XKEY-[0-9a-f]{16}</secretPattern>
									</secretPatterns>
									<useDefaultPatterns>true</useDefaultPatterns>
								</noSecrets>
								<mcpServersValid>
									<mcpFile>${project.basedir}/.mcp.json</mcpFile>
									<requiredServers>
										<requiredServer>remote</requiredServer>
									</requiredServers>
									<forbiddenServers>
										<forbiddenServer>deprecated</forbiddenServer>
									</forbiddenServers>
									<allowedTypes>
										<allowedType>stdio</allowedType>
										<allowedType>http</allowedType>
										<allowedType>sse</allowedType>
									</allowedTypes>
								</mcpServersValid>
								<mcpConfigFormat>
									<mcpFile>${project.basedir}/.mcp.json</mcpFile>
									<requireHttps>true</requireHttps>
								</mcpConfigFormat>
								<okfBundleFormat>
									<bundleDir>${project.basedir}/okf</bundleDir>
									<okfVersion>0.2</okfVersion>
									<requiredKeys>
										<requiredKey>title</requiredKey>
									</requiredKeys>
									<requireIndex>true</requireIndex>
								</okfBundleFormat>
			""";

	/** What {@link #COMPLETE} addresses its files through: the project being built. */
	private static final String PROJECT_BASEDIR = "${project.basedir}";

	private final String xml;

	RuleConfiguration(String xml) {
		this.xml = xml;
	}

	/** Every shipped rule, with every parameter it accepts, against the well-formed fixture. */
	static RuleConfiguration complete() {
		return new RuleConfiguration(COMPLETE);
	}

	/**
	 * The same complete block, addressed at {@code baseDir} rather than at the project
	 * whose build evaluates it. That separation is what lets
	 * {@link ForeignRepositoryEnforcementIT} enforce a repository it did not write
	 * without putting a {@code pom.xml} into it: the build runs in a directory of its
	 * own, and only the rule parameters point at the checkout.
	 * <p>
	 * A directory a real repository has no reason to hold — {@code .claude/skills},
	 * {@code okf} — stays configured on purpose. The claim under test is that a rule
	 * pointed somewhere a project has nothing says so plainly, and a block that
	 * quietly dropped those rules would be asserting the opposite.
	 *
	 * @param baseDir the directory to address, as a pom writes one: a literal path or
	 *                a property expression Maven interpolates
	 */
	static RuleConfiguration against(String baseDir) {
		return new RuleConfiguration(COMPLETE.replace(PROJECT_BASEDIR, baseDir));
	}

	String xml() {
		return xml;
	}

	/** The rules this block configures, by the name the pom addresses each one with. */
	Set<String> ruleNames() {
		Set<String> names = new LinkedHashSet<>();
		for (Element rule : rules().children()) {
			names.add(rule.tagName());
		}
		return names;
	}

	/** The parameters this block sets on one rule, by the element name each is written as. */
	Set<String> parametersOf(String ruleName) {
		Set<String> parameters = new LinkedHashSet<>();
		for (Element rule : rules().children()) {
			addParameters(rule, ruleName, parameters);
		}
		return parameters;
	}

	private void addParameters(Element rule, String ruleName, Set<String> parameters) {
		if (rule.tagName().equals(ruleName)) {
			for (Element parameter : rule.children()) {
				parameters.add(parameter.tagName());
			}
		}
	}

	/**
	 * Parsed with the XML parser, which preserves the case of a tag name; the HTML
	 * one would fold {@code claudeMdFormat} to lower case and match no rule.
	 */
	private Element rules() {
		return Jsoup.parse("<rules>" + xml + "</rules>", "", Parser.xmlParser())
				.selectFirst("rules");
	}
}
