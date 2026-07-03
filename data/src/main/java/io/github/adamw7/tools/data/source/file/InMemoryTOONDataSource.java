package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

/**
 * Data source for TOON (Token-Oriented Object Notation) format files.
 * TOON is a compact, human-readable format designed to minimize tokens for LLM prompts.
 *
 * Supports:
 * - Key-value pairs (key: value)
 * - Primitive arrays (key[N]: val1,val2,...)
 * - Tabular arrays (key[N]{field1,field2}: row1,row2...)
 * - Nested objects via indentation
 *
 * <p>The whole document is flattened eagerly into {@link #fieldsMap}; parsing is delegated to
 * the shared {@link ToonFlattener}, the same grammar the streaming
 * {@link IterableTOONDataSource} drives, so the two cannot diverge.</p>
 */
public class InMemoryTOONDataSource extends AbstractInMemoryMapDataSource {

	public InMemoryTOONDataSource(InputStream inputStream) {
		super(inputStream);
	}

	public InMemoryTOONDataSource(String filePath) {
		super(filePath);
	}

	@Override
	protected void parse() {
		ToonFlattener flattener = new ToonFlattener(fieldsMap::put);
		while (scanner.hasNextLine()) {
			flattener.accept(scanner.nextLine());
		}
		flattener.finish();
	}
}
