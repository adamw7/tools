package io.github.adamw7.tools.enforcer;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShellScriptRuleTest {

    private ShellScriptRule rule;

    @BeforeEach
    void setUp() {
        rule = new ShellScriptRule();
    }

    @Test
    void validScriptPassesCheck(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("valid.sh"), "#!/bin/bash\necho hello\n");
        rule.setScriptsDirectory(tempDir.toString());
        assertDoesNotThrow(() -> rule.execute());
    }

    @Test
    void invalidScriptFailsBuild(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("broken.sh"), "#!/bin/bash\n(((\n");
        rule.setScriptsDirectory(tempDir.toString());
        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    @Test
    void emptyDirectoryPassesCheck(@TempDir Path tempDir) {
        rule.setScriptsDirectory(tempDir.toString());
        assertDoesNotThrow(() -> rule.execute());
    }

    @Test
    void nullDirectoryThrowsException() {
        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    @Test
    void nonExistentDirectoryThrowsException() {
        rule.setScriptsDirectory("/nonexistent/path/to/scripts");
        assertThrows(EnforcerRuleException.class, () -> rule.execute());
    }

    @Test
    void nonShellFilesAreIgnored(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("not-a-script.txt"), "this is not a shell script");
        rule.setScriptsDirectory(tempDir.toString());
        assertDoesNotThrow(() -> rule.execute());
    }
}
