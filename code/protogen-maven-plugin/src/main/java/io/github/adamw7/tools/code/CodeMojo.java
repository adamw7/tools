package io.github.adamw7.tools.code;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.protobuf.GeneratedMessageV3;

import io.github.adamw7.tools.code.gen.Code;

@Mojo(name = "code-generator", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CodeMojo extends AbstractMojo {
	
	private final static Logger log = LogManager.getLogger(CodeMojo.class.getName());
	
	public final static String DEFAULT_PACKAGE = "io.github.adamw7.tools.code.protos";
	
	@Parameter(property = "generatedsourcesdir", required = true)
	protected String generatedSourcesDir;
	
	@Parameter(property = "pkg", required = false, defaultValue = DEFAULT_PACKAGE)
	protected String pkg;
	
	@Override
	public void execute() {
		log.info("Executing {} maven plugin", this);
		Set<Class<? extends GeneratedMessageV3>> allMessages = new MessagesFinder(pkg).execute();
		new Code(generatedSourcesDir).genBuilders(allMessages);
	}

}
