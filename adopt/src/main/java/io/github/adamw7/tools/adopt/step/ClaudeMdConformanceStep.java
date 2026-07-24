package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.command.CommandRunner;

/**
 * Makes the adoption self-consistent: after {@link ClaudeInitStep} generates a
 * {@code CLAUDE.md}, this reshapes it with a {@link ClaudeMdConformer} so it
 * satisfies the {@code claudeMdFormat} rule {@link EnforcerStep} wires into the
 * build, and writes a companion {@code AGENTS.md} so the reference the rule
 * expects resolves to a real file. Without it the adoption fails its own
 * {@link VerifyStep}, because a generic {@code claude init} produces natural,
 * project-specific headings and no {@code AGENTS.md} reference while the rule
 * demands a fixed set of headings plus that reference.
 *
 * <p>The step runs before the first commit so the normalised {@code CLAUDE.md}
 * and its companion {@code AGENTS.md} are committed together. It never overwrites
 * an {@code AGENTS.md} the project already carries, and reshaping an already
 * conforming {@code CLAUDE.md} leaves it unchanged, so the step is idempotent on
 * re-adoption.
 */
public class ClaudeMdConformanceStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(ClaudeMdConformanceStep.class);

	static final String CLAUDE_MD = "CLAUDE.md";

	private final ClaudeMdConformer conformer;
	private final AssetInstaller agentsMdInstaller;

	public ClaudeMdConformanceStep() {
		this(new ClaudeMdConformer(), new AssetInstaller(AdoptionAssets.AGENTS_MD_FILE, AdoptionAssets.AGENTS_MD));
	}

	ClaudeMdConformanceStep(ClaudeMdConformer conformer, AssetInstaller agentsMdInstaller) {
		this.conformer = conformer;
		this.agentsMdInstaller = agentsMdInstaller;
	}

	@Override
	public String name() {
		return "conform";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path checkout = context.repositoryDirectory();
		installAgentsMd(checkout);
		conformClaudeMd(checkout);
	}

	private void installAgentsMd(Path checkout) {
		if (agentsMdInstaller.install(checkout)) {
			log.info("Wrote companion {}", agentsMdInstaller.relativePath());
		} else {
			log.info("{} already exists; left unchanged", agentsMdInstaller.relativePath());
		}
	}

	private void conformClaudeMd(Path checkout) {
		Path claudeMd = checkout.resolve(CLAUDE_MD);
		String original = read(claudeMd);
		String conformed = conformer.conform(original);
		if (conformed.equals(original)) {
			log.info("{} already satisfies the claudeMdFormat rule; left unchanged", CLAUDE_MD);
		} else {
			write(claudeMd, conformed);
			log.info("Normalised {} to satisfy the claudeMdFormat rule", CLAUDE_MD);
		}
	}

	private String read(Path claudeMd) {
		if (!Files.isRegularFile(claudeMd)) {
			throw new AdoptionException(name() + " requires " + CLAUDE_MD + " but it was not found in "
					+ claudeMd.getParent());
		}
		try {
			return Files.readString(claudeMd);
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not read " + claudeMd, e);
		}
	}

	private void write(Path claudeMd, String content) {
		try {
			Files.writeString(claudeMd, content);
		} catch (IOException e) {
			throw new AdoptionException(name() + " could not write " + claudeMd, e);
		}
	}
}
