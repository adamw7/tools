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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.google.protobuf.GeneratedMessageV3;

import io.github.adamw7.tools.code.gen.Code;

@Mojo(name = "code-generator", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CodeMojo extends AbstractMojo {

	private final static Logger log = LogManager.getLogger(CodeMojo.class.getName());

	@Parameter(property = "generatedsourcesdir", required = true)
	protected String generatedSourcesDir;

	@Parameter(property = "pkgs", required = true)
	protected String[] pkgs;
	
	@Parameter(property = "outputpackage", required = true)
	protected String outputpackage;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	protected MavenProject project;

	@Override
	public void execute() {
		log.info("Executing {} maven plugin", this);
		extendClassPath();
		Set<Class<? extends GeneratedMessageV3>> allMessages = new MessagesFinder(pkgs).execute();
		new Code(generatedSourcesDir, outputpackage).genBuilders(allMessages);
	}

	@SuppressWarnings("unchecked")
	private void extendClassPath() {
		try {
			Set<URL> urls = new HashSet<>();
			List<String> elements = project.getRuntimeClasspathElements();

			for (String element : elements) {
				if (element != null) {
					urls.add(new File(element).toURI().toURL());					
				}
			}

			ClassLoader contextClassLoader = URLClassLoader.newInstance(urls.toArray(new URL[urls.size()]),
					Thread.currentThread().getContextClassLoader());

			Thread.currentThread().setContextClassLoader(contextClassLoader);
		} catch (DependencyResolutionRequiredException | MalformedURLException e) {
			throw new MojoException(e);
		} 
	}

}
