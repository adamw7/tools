package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.github.adamw7.tools.markdown.MarkdownDocument;

/**
 * Reads and parses each input file once per build, however many rules ask for it.
 *
 * <p>The rules are deliberately independent, and the cost of that is that they
 * overlap: {@code CLAUDE.md} is read by five of them and {@code settings.json} by
 * four. Sharing the result costs them none of that independence, because what is
 * shared is the file's content and not any rule's reading of it.
 *
 * <p>An entry is keyed by what the file <em>is</em> rather than where it is: the
 * path with its modification time and size. A file rewritten during the build —
 * which {@code autoFix} does — therefore misses, with nothing having to remember
 * to invalidate it. Only successful reads and parses are kept; a failure is
 * re-read, since the rule that asked is about to fail the build anyway.
 *
 * <p>The cache is static because a Maven session builds each rule afresh, so
 * anything held per instance is held for exactly one rule. It is bounded and
 * dropped wholesale when it fills: entries are worth keeping for one build.
 */
final class DocumentCache {

	/**
	 * Roughly an order of magnitude more entries than a build touches, so the clear
	 * below is a safety net against a long-lived JVM (an IDE, a daemon, the
	 * integration tests' repeated runs) rather than something a build reaches.
	 */
	private static final int MAX_ENTRIES = 512;

	/**
	 * One map for every kind of entry, keyed by the file and what was made of it.
	 * Values are held as {@link Object} and cast back on the way out, so nothing here
	 * names the type of any particular parse: mentioning Jackson dragged it onto the
	 * classpath of every caller that loads a rule, and the adopt module's contract
	 * test failed to load a class it never uses.
	 */
	private static final Map<Entry, Object> CACHE = new ConcurrentHashMap<>();

	private DocumentCache() {
	}

	/**
	 * The file's text, read through {@code reader} on a miss.
	 *
	 * @param reader reads the file, answering empty when it is not readable as text
	 */
	static Optional<String> text(File file, Supplier<Optional<String>> reader) {
		return cached(Kind.TEXT, file, reader);
	}

	/** The file's parsed Markdown, parsed from {@code content} on a miss. */
	static MarkdownDocument markdown(File file, String content) {
		return DocumentCache.<MarkdownDocument>cached(Kind.MARKDOWN, file,
				() -> Optional.of(MarkdownDocument.parse(content)))
				.orElseGet(() -> MarkdownDocument.parse(content));
	}

	/**
	 * The file's parsed form, parsed through {@code parser} on a miss. A parse that
	 * failed answers empty and is not kept, so the violation it collected is
	 * collected again for the next rule that asks.
	 */
	static <T> Optional<T> parsed(File file, Supplier<Optional<T>> parser) {
		return cached(Kind.PARSED, file, parser);
	}

	@SuppressWarnings("unchecked")
	private static <T> Optional<T> cached(Kind kind, File file, Supplier<Optional<T>> load) {
		Optional<Key> key = Key.of(file);
		if (key.isEmpty()) {
			return load.get();
		}
		Entry entry = new Entry(key.get(), kind);
		Object hit = CACHE.get(entry);
		if (hit != null) {
			return Optional.of((T) hit);
		}
		Optional<T> loaded = load.get();
		loaded.ifPresent(value -> store(entry, value));
		return loaded;
	}

	private static void store(Entry entry, Object value) {
		if (CACHE.size() >= MAX_ENTRIES) {
			CACHE.clear();
		}
		CACHE.put(entry, value);
	}

	/** Drops everything held, so a test can prove a read happened rather than a hit. */
	static void clear() {
		CACHE.clear();
	}

	/** What was made of a file, so one file's text and its parse are two entries. */
	private enum Kind {
		TEXT, MARKDOWN, PARSED
	}

	private record Entry(Key key, Kind kind) {
	}

	/**
	 * What identifies a file's content for the length of a build: where it is, when
	 * it was last written, how big it is. A file whose stamp cannot be read yields no
	 * key, and the caller reads it directly — the rule's own missing-file diagnosis
	 * beats anything this class could invent.
	 */
	private record Key(Path path, long lastModified, long size) {

		static Optional<Key> of(File file) {
			try {
				Path path = file.toPath().toAbsolutePath().normalize();
				BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
				return Optional.of(new Key(path, nanoseconds(attributes), attributes.size()));
			} catch (IOException | RuntimeException e) {
				return Optional.empty();
			}
		}

		/**
		 * The finest modification time the filesystem records. Truncating to
		 * milliseconds leaves a window in which two writes stamp identically, and an
		 * equal-length rewrite inside it would be served from the cache.
		 */
		private static long nanoseconds(BasicFileAttributes attributes) {
			return attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS);
		}
	}
}
