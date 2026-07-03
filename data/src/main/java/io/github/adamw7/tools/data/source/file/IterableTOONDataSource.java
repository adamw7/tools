package io.github.adamw7.tools.data.source.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Iterable counterpart of {@link InMemoryTOONDataSource}. Reads TOON one line at a time and
 * emits flattened {@code {key, value}} rows through the shared {@link ToonFlattener}, buffering
 * only the pairs produced by the line just read, so memory use is bounded by nesting depth and
 * the widest single line rather than by document size.
 */
public class IterableTOONDataSource extends AbstractIterableFileSource {

	private final Deque<String[]> pending = new ArrayDeque<>();
	private BufferedReader reader;
	private ToonFlattener flattener;
	private boolean exhausted;

	public IterableTOONDataSource(String fileName) {
		super(fileName);
	}

	public IterableTOONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	protected void initialise(InputStream stream) throws IOException {
		reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		pending.clear();
		flattener = new ToonFlattener(this::enqueue);
		exhausted = false;
	}

	@Override
	protected String[] readNextRow() throws IOException {
		String[] row = pending.poll();
		while (row == null && !exhausted) {
			pullLine();
			row = pending.poll();
		}
		return row;
	}

	private void pullLine() throws IOException {
		String line = reader.readLine();
		if (line == null) {
			exhausted = true;
			flattener.finish();
		} else {
			flattener.accept(line);
		}
	}

	private void enqueue(String key, String value) {
		pending.addLast(new String[] { key, value });
	}

	@Override
	protected void releaseResources() throws IOException {
		pending.clear();
		flattener = null;
		if (reader != null) {
			reader.close();
		}
	}
}
