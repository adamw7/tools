package io.github.adamw7.tools.enforcer.e2e;

/**
 * The {@code pom.xml} that runs a {@code <rules>} block: the maven-enforcer-plugin
 * the repository itself pins, the rule jar under test as a <em>plugin</em>
 * dependency — the only class path a maven-enforcer rule is loaded from — and one
 * {@code validate}-phase execution, so a build costs a second.
 * <p>
 * Two end-to-end tests need this build and disagree about only two things: what the
 * project is called, and where the rules are pointed. {@link FixtureProject} writes
 * it into a project it has just laid out and points the rules at that project;
 * {@link ForeignRepositoryEnforcementIT} writes it into a directory of its own and
 * points them at a repository it has cloned, so the repository under test is left
 * exactly as {@code git} produced it. A second copy of the template would let those
 * two builds drift apart — one picking up a plugin version or a dependency the
 * other never sees — and then a rule proved to bind in one would say nothing about
 * the other.
 */
final class EnforcerPom {

	private static final String TEMPLATE = """
			<project xmlns="http://maven.apache.org/POM/4.0.0">
				<modelVersion>4.0.0</modelVersion>
				<groupId>io.github.adamw7.enforcer.e2e</groupId>
				<artifactId>%s</artifactId>
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

	private final BuildEnvironment environment;
	private final String enforcerPluginVersion;

	EnforcerPom(BuildEnvironment environment, String enforcerPluginVersion) {
		this.environment = environment;
		this.enforcerPluginVersion = enforcerPluginVersion;
	}

	/**
	 * The pom of a project called {@code artifactId} that enforces {@code rulesXml}.
	 *
	 * @param artifactId what the build calls itself, so a log says which of the
	 *                   end-to-end builds it came from
	 * @param rulesXml   the {@code <rules>} children, written as a pom writes them
	 */
	String enforcing(String artifactId, String rulesXml) {
		return TEMPLATE.formatted(artifactId, enforcerPluginVersion, BuildEnvironment.GROUP_ID,
				BuildEnvironment.ARTIFACT_ID, environment.version(), rulesXml);
	}
}
