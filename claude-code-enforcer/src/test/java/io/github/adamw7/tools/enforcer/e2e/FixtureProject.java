package io.github.adamw7.tools.enforcer.e2e;

import static io.github.adamw7.tools.enforcer.rule.TestFiles.readString;
import static io.github.adamw7.tools.enforcer.rule.TestFiles.writeString;

import java.nio.file.Path;

/**
 * A throwaway project laid out the way a repository that has adopted Claude Code
 * is: the two agent documents, a README, the {@code .claude} configuration with a
 * skill, a sub-agent, a command, settings and a hook, an {@code .mcp.json}, a
 * plugin manifest and a {@code .gitignore} — plus the pom that runs the rules
 * against them.
 * <p>
 * Everything is well formed to begin with, so a test states its case by breaking
 * exactly one thing and watching the build react. Rule parameters are written into
 * the pom as {@code ${project.basedir}} paths, as a real project writes them, which
 * is what puts Maven's own interpolation between the configuration and the rule.
 */
final class FixtureProject {

	private static final String CLAUDE_MD = """
			# CLAUDE.md

			Guidance for agents working in this fixture, which defers to
			[AGENTS.md](AGENTS.md) as the single source of truth.

			@docs/imported.md
			@ignored/never-loaded.md

			## Project

			A fixture reactor of two modules, alpha and beta.

			## Java version

			Java 25.

			## Maven

			Build with `mvn install` from the fixture root.

			## Principles for Java Development

			SOLID principles, clean code, short methods.

			## Testing

			Unit tests for all new logic, and integration tests for what needs
			something real.

			## Dependencies

			Use the existing dependencies; ask before adding one.
			""";

	private static final String AGENTS_MD = """
			# AGENTS.md

			The source of truth for this fixture, summarised in
			[CLAUDE.md](CLAUDE.md).

			## Project overview

			A fixture project the enforcer end-to-end tests build. Java 25, proto3.

			## Module layout

			The reactor holds two modules: alpha and beta.

			## Environment & toolchain

			Java 25 and Maven.

			## Build, test, and run

			`mvn install` builds and tests everything.

			## Code style & conventions

			Match the surrounding code.

			## Releasing

			Tag a release and let the pipeline publish it.

			## Pull requests & commits

			Conventional commit messages and focused changes.
			""";

	private static final String README_MD = """
			# Fixture

			A fixture project that generates proto3 messages. See
			[AGENTS.md](AGENTS.md) for the details.
			""";

	private static final String IMPORTED_MD = """
			# Imported memory

			Context that CLAUDE.md pulls in with a memory import.
			""";

	private static final String GITIGNORE = """
			target/
			.claude/settings.local.json
			""";

	/**
	 * The reactor whose modules the docs must document. It is a plain file the
	 * module-map rule reads rather than a pom any build runs, which is why it can
	 * name modules that have no directory — including the one the rule is configured
	 * to ignore.
	 */
	private static final String REACTOR_POM = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
				<modelVersion>4.0.0</modelVersion>
				<groupId>io.github.adamw7.enforcer.e2e</groupId>
				<artifactId>reactor</artifactId>
				<version>1.0</version>
				<packaging>pom</packaging>
				<modules>
					<module>alpha</module>
					<module>nested/beta</module>
					<module>gamma</module>
				</modules>
			</project>
			""";

	private static final String SETTINGS_JSON = """
			{
			  "permissions": {
			    "allow": ["Bash(mvn *)", "Read"],
			    "deny": ["Bash(rm *)"]
			  },
			  "hooks": {
			    "SessionStart": [
			      {
			        "hooks": [
			          {
			            "type": "command",
			            "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh"
			          }
			        ]
			      }
			    ]
			  }
			}
			""";

	private static final String SESSION_START_HOOK = """
			#!/usr/bin/env bash
			set -euo pipefail
			echo "fixture session start"
			""";

	private static final String SKILL_MD = """
			---
			name: sample-skill
			description: Explains the fixture project to an agent that opens it.
			---

			# Sample skill

			Body of the skill.
			""";

	private static final String SUB_AGENT_MD = """
			---
			name: sample-agent
			description: Reviews the fixture project on request.
			model: sonnet
			---

			Instructions for the sub-agent.
			""";

	private static final String COMMAND_MD = """
			---
			description: Builds the fixture project from the repository root.
			model: sonnet
			---

			Run the build.
			""";

	private static final String PLUGIN_JSON = """
			{
			  "name": "sample-plugin",
			  "version": "1.0.0",
			  "description": "A plugin manifest the fixture ships."
			}
			""";

	private static final String MCP_JSON = """
			{
			  "mcpServers": {
			    "remote": {
			      "type": "http",
			      "url": "https://example.invalid/mcp",
			      "headers": { "X-Fixture": "sample" }
			    },
			    "local": {
			      "type": "stdio",
			      "command": "java",
			      "args": ["-jar", "server.jar"],
			      "env": { "MODE": "fixture" }
			    }
			  }
			}
			""";

	/** A bundle-root listing: the one index allowed frontmatter, and only for the version. */
	private static final String OKF_INDEX = """
			---
			okf_version: "0.2"
			---

			# Files

			* [sample.md](sample.md) - a sample concept.
			""";

	private static final String OKF_CONCEPT = """
			---
			type: "Sample Concept"
			title: "Sample"
			description: "A conformant concept the enforcer rule reads."
			tags: ["fixture"]
			status: stable
			stale_after: 2027-01-01
			generated: { by: "fixture/1", at: "2026-08-03T10:15:30Z" }
			---

			# Body

			Nothing here depends on anything.
			""";

	/**
	 * The build itself. The rule jar is a plugin dependency rather than a project
	 * dependency, because that is the only class path a maven-enforcer rule is loaded
	 * from, and the whole configuration sits in one {@code validate}-phase execution
	 * so a fixture build costs a second.
	 */
	private static final String POM_TEMPLATE = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
				<modelVersion>4.0.0</modelVersion>
				<groupId>io.github.adamw7.enforcer.e2e</groupId>
				<artifactId>fixture</artifactId>
				<version>1.0</version>
				<packaging>pom</packaging>
				<build>
					<plugins>
						<plugin>
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-enforcer-plugin</artifactId>
							<version>%s</version>
							<dependencies>
								<dependency>
									<groupId>%s</groupId>
									<artifactId>%s</artifactId>
									<version>%s</version>
								</dependency>
							</dependencies>
							<executions>
								<execution>
									<id>enforce-claude-code</id>
									<phase>validate</phase>
									<goals>
										<goal>enforce</goal>
									</goals>
									<configuration>
										<rules>
			%s
										</rules>
									</configuration>
								</execution>
							</executions>
						</plugin>
					</plugins>
				</build>
			</project>
			""";

	private final Path root;
	private final BuildEnvironment environment;
	private final String enforcerPluginVersion;

	FixtureProject(Path root, BuildEnvironment environment, String enforcerPluginVersion) {
		this.root = root;
		this.environment = environment;
		this.enforcerPluginVersion = enforcerPluginVersion;
	}

	Path root() {
		return root;
	}

	Path path(String relativePath) {
		return root.resolve(relativePath);
	}

	String read(String relativePath) {
		return readString(path(relativePath));
	}

	FixtureProject write(String relativePath, String content) {
		writeString(path(relativePath), content);
		return this;
	}

	/** Every file the shipped rules look at, all of them well formed. */
	FixtureProject layOutWellFormedProject() {
		write("CLAUDE.md", CLAUDE_MD);
		write("AGENTS.md", AGENTS_MD);
		write("README.md", README_MD);
		write("docs/imported.md", IMPORTED_MD);
		write(".gitignore", GITIGNORE);
		write("reactor-pom.xml", REACTOR_POM);
		write(".claude/settings.json", SETTINGS_JSON);
		writeHook();
		write(".claude/skills/sample-skill/SKILL.md", SKILL_MD);
		write(".claude/agents/sample-agent.md", SUB_AGENT_MD);
		write(".claude/commands/sample-command.md", COMMAND_MD);
		write(".claude-plugin/plugin.json", PLUGIN_JSON);
		write(".mcp.json", MCP_JSON);
		write("okf/index.md", OKF_INDEX);
		write("okf/sample.md", OKF_CONCEPT);
		return this;
	}

	/** Writes the pom that runs {@code rulesXml}, and returns the project to build. */
	FixtureProject enforcing(String rulesXml) {
		write("pom.xml", POM_TEMPLATE.formatted(enforcerPluginVersion, BuildEnvironment.GROUP_ID,
				BuildEnvironment.ARTIFACT_ID, environment.version(), rulesXml));
		return this;
	}

	/**
	 * A hook is a script Claude Code runs, so the executable bit is part of being well
	 * formed and the rule that checks it needs the fixture to carry one.
	 */
	private void writeHook() {
		Path hook = path(".claude/hooks/session-start.sh");
		writeString(hook, SESSION_START_HOOK);
		hook.toFile().setExecutable(true);
	}
}
