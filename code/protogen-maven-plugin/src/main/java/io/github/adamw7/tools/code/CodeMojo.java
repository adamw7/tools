package io.github.adamw7.tools.code;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import com.google.protobuf.GeneratedMessage;

import io.github.adamw7.tools.code.gen.Code;

@Mojo(name = "code-generator", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class CodeMojo extends AbstractMojo {

	@Parameter(property = "generatedsourcesdir", required = true)
	protected String generatedSourcesDir;

	@Parameter(property = "pkgs", required = true)
	protected String[] pkgs;
	
	@Parameter(property = "outputpackage", required = true)
	protected String outputpackage;

	@Parameter(defaultValue = "${project.runtimeClasspathElements}", required = true, readonly = true)
	protected List<String> runtimeClasspathElements;

	/**
	 * Every failure is reported as a {@link MojoExecutionException}: the goal
	 * runs against packages a consumer's pom names, so a mistake there must read
	 * as a build failure attributed to this plugin, not as a runtime exception
	 * Maven prints as an internal error with "this is likely a bug in the plugin".
	 */
	@Override
	public void execute() throws MojoExecutionException {
		getLog().info("Generating builders for " + Arrays.toString(pkgs) + " into " + outputpackage);
		// The only shared state this goal touches is the thread context classloader
		// (Reflections scans through it), so restoring it keeps a parallel `-T` build
		// from leaking one project's classpath into whatever the thread runs next.
		// Closing the loader releases the handle it holds on every jar it opened,
		// which on Windows is also what unlocks those files for the rest of the build.
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		try (URLClassLoader projectClassLoader = extendClassPath()) {
			Set<Class<? extends GeneratedMessage>> allMessages = new MessagesFinder(getLog(), pkgs).execute();
			new Code(getLog(), generatedSourcesDir, outputpackage).genBuilders(allMessages);
		} catch (IOException | RuntimeException e) {
			// Maven appends the cause's own message when it reports the failed goal,
			// so this one only has to say which generation it was.
			throw new MojoExecutionException("Cannot generate builders into " + outputpackage, e);
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	/**
	 * Puts the project's runtime classpath on the thread context classloader,
	 * which is what {@link MessagesFinder} scans through, so a project's own
	 * generated messages are visible to the scan. The elements keep the order
	 * Maven resolved them in, so the scan sees the same classpath twice running.
	 * Package-private so the behaviour can be exercised directly: {@link #execute()}
	 * closes the loader and restores the original classloader before it returns,
	 * leaving no other point from which the extension can be observed.
	 *
	 * @return the loader now on the thread, for the caller to close
	 */
	URLClassLoader extendClassPath() {
		// A List, never a Set: URL.equals resolves the host to compare, so hashing
		// one is a blocking DNS lookup, and the hash order would hand the scan a
		// differently ordered classpath from one run to the next.
		List<URL> urls = new ArrayList<>();

		for (String element : runtimeClasspathElements) {
			if (element != null) {
				urls.add(toUrl(element));
			}
		}

		URLClassLoader contextClassLoader = new URLClassLoader(urls.toArray(new URL[0]),
				Thread.currentThread().getContextClassLoader());

		Thread.currentThread().setContextClassLoader(contextClassLoader);
		return contextClassLoader;
	}

	private URL toUrl(String element) {
		try {
			return new File(element).toURI().toURL();
		} catch (MalformedURLException e) {
			throw new MojoException("Not a usable classpath element: " + element, e);
		}
	}

}
