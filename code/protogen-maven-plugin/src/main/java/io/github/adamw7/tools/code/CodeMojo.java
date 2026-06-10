package io.github.adamw7.tools.code;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.gen.Code;

@Mojo(name = "code-generator", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = false, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class CodeMojo extends AbstractMojo {

	private final static Logger log = LogManager.getLogger(CodeMojo.class.getName());

	@Parameter(property = "generatedsourcesdir", required = true)
	protected String generatedSourcesDir;

	@Parameter(property = "pkgs", required = true)
	protected String[] pkgs;
	
	@Parameter(property = "outputpackage", required = true)
	protected String outputpackage;

	@Parameter(defaultValue = "${project.runtimeClasspathElements}", required = true, readonly = true)
	protected List<String> runtimeClasspathElements;

	@Override
	public void execute() {
		log.info("Executing {} maven plugin", this);
		extendClassPath();
		Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder(pkgs).execute();
		new Code(generatedSourcesDir, outputpackage).genBuilders(allMessages);
	}

	private void extendClassPath() {
		try {
			Set<URL> urls = new HashSet<>();

			for (String element : runtimeClasspathElements) {
				if (element != null) {
					urls.add(new File(element).toURI().toURL());
				}
			}

			ClassLoader contextClassLoader = new URLClassLoader(urls.toArray(new URL[]{}),
					Thread.currentThread().getContextClassLoader());

			Thread.currentThread().setContextClassLoader(contextClassLoader);
		} catch (MalformedURLException e) {
			throw new MojoException(e);
		}
	}

}
