package io.github.adamw7.tools.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "code-generator", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CodeMojo extends AbstractMojo {
	
	private final static Logger log = LogManager.getLogger(CodeMojo.class.getName());

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		log.info("Executing " + this + " maven plugin");
	}

}
