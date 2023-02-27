package io.github.adamw7.tools.code;

import org.junit.jupiter.api.Test;

public class CodeMojoTest {

	@Test
	public void happyPath() {
		new CodeMojo().execute();
	}
}
