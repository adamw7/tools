package io.github.adamw7.tools.enforcer;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Named("shellScriptRule")
public class ShellScriptRule extends AbstractEnforcerRule {

    private String scriptsDirectory;

    @Override
    public void execute() throws EnforcerRuleException {
        validateConfiguration();
        List<String> errors = collectSyntaxErrors(Path.of(scriptsDirectory));
        if (!errors.isEmpty()) {
            throw new EnforcerRuleException("Shell script syntax errors:\n" + String.join("\n", errors));
        }
    }

    private void validateConfiguration() throws EnforcerRuleException {
        if (scriptsDirectory == null || scriptsDirectory.isBlank()) {
            throw new EnforcerRuleException("scriptsDirectory must be configured");
        }
        if (!Files.isDirectory(Path.of(scriptsDirectory))) {
            throw new EnforcerRuleException("scriptsDirectory does not exist: " + scriptsDirectory);
        }
    }

    private List<String> collectSyntaxErrors(Path directory) throws EnforcerRuleException {
        List<String> errors = new ArrayList<>();
        try (Stream<Path> scripts = Files.walk(directory)) {
            scripts.filter(path -> path.toString().endsWith(".sh"))
                   .forEach(script -> checkSyntax(script, errors));
        } catch (IOException e) {
            throw new EnforcerRuleException("Failed to scan scripts directory: " + e.getMessage());
        }
        return errors;
    }

    private void checkSyntax(Path script, List<String> errors) {
        try {
            Process process = new ProcessBuilder("bash", "-n", script.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                errors.add(script.getFileName() + ": " + output.trim());
            }
        } catch (IOException e) {
            errors.add(script.getFileName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add(script.getFileName() + ": interrupted");
        }
    }

    public void setScriptsDirectory(String scriptsDirectory) {
        this.scriptsDirectory = scriptsDirectory;
    }
}
