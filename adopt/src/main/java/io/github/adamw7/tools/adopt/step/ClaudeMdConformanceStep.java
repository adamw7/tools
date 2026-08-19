package io.github.adamw7.tools.adopt.step;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionFiles;
import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.markdown.LineTerminators;

/**
 * Makes the adoption self-consistent: after {@link ClaudeInitStep} generates a
 * {@code CLAUDE.md}, this reshapes it with a {@link ClaudeMdConformer} so it
 * satisfies the guard {@link EnforcerStep} wires into the build, and writes a
 * companion {@code AGENTS.md} so the reference resolves to a real file. Without it
 * an adoption whose guard is the {@code claudeMdFormat} rule fails its own
 * {@link VerifyStep}.
 *
 * <p>How much reshaping that takes is the detected {@link BuildSystem}'s to say,
 * through {@link BuildSystem#requiredClaudeMdSections()}: the Maven path gets the
 * full set of sections, while a guard asking only for a non-empty file leaves the
 * document as {@code claude init} wrote it. Reshaping past what the guard checks
 * would put a Java project's headings into repositories that are not one.
 *
 * <p>The step runs before the first commit so both files are committed together.
 * It never overwrites an {@code AGENTS.md} the project already carries, and
 * reshaping an already conforming {@code CLAUDE.md} leaves it unchanged, so the
 * step is idempotent on re-adoption.
 */
public class ClaudeMdConformanceStep implements AdoptionStep {

	private static final Logger log = LogManager.getLogger(ClaudeMdConformanceStep.class);

	private static final String CLAUDE_MD = AdoptionAssets.CLAUDE_MD_FILE;

	private final List<BuildSystem> buildSystems;
	private final AssetInstaller agentsMdInstaller;

	/** Detects the checkout's build system among {@link BuildSystems#DEFAULTS}. */
	public ClaudeMdConformanceStep() {
		this(BuildSystems.DEFAULTS);
	}

	public ClaudeMdConformanceStep(List<BuildSystem> buildSystems) {
		this(buildSystems, AdoptionAssets.agentsMd());
	}

	ClaudeMdConformanceStep(List<BuildSystem> buildSystems, AssetInstaller agentsMdInstaller) {
		this.buildSystems = List.copyOf(buildSystems);
		this.agentsMdInstaller = agentsMdInstaller;
	}

	@Override
	public String name() {
		return "conform";
	}

	@Override
	public void execute(AdoptionContext context, CommandRunner runner) {
		Path checkout = context.repositoryDirectory();
		agentsMdInstaller.install(checkout);
		conformClaudeMd(checkout);
	}

	/**
	 * The reshaped text is put back on the file's own line terminators before it is
	 * compared or written. {@link ClaudeMdConformer} works in LF throughout, so a
	 * CRLF {@code CLAUDE.md} would otherwise come back with every one of its lines
	 * changed — a whole-file diff in the adoption's first commit, and a file the
	 * step no longer recognises as already conforming on a re-adoption.
	 */
	private void conformClaudeMd(Path checkout) {
		Path claudeMd = checkout.resolve(CLAUDE_MD);
		String original = read(claudeMd);
		String conformed = LineTerminators.matching(conformer(checkout).conform(original), original);
		if (conformed.equals(original)) {
			log.info("{} already satisfies the claudeMdFormat rule; left unchanged", CLAUDE_MD);
		} else {
			AdoptionFiles.write(claudeMd, conformed, CLAUDE_MD);
			log.info("Normalised {} to satisfy the claudeMdFormat rule", CLAUDE_MD);
		}
	}

	/**
	 * Reshapes to the contract of the guard {@link EnforcerStep} is about to wire
	 * into this very checkout, detected from the same build-system list that step is
	 * given, so the two never disagree about what the file has to carry. A list
	 * detection comes up empty on — one configured without a catch-all, since
	 * {@link BuildSystems#DEFAULTS} always matches — leaves no guard to satisfy and so
	 * demands no section.
	 */
	private ClaudeMdConformer conformer(Path checkout) {
		return new ClaudeMdConformer(BuildSystems.detect(buildSystems, checkout)
				.map(BuildSystem::requiredClaudeMdSections)
				.orElseGet(List::of));
	}

	private String read(Path claudeMd) {
		if (!Files.isRegularFile(claudeMd)) {
			throw new AdoptionException(name() + " requires " + CLAUDE_MD + " but it was not found in "
					+ claudeMd.getParent());
		}
		return AdoptionFiles.read(claudeMd, CLAUDE_MD);
	}
}
