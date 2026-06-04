package io.github.adamw7.tools.data.source.file;

import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.yaml.snakeyaml.LoaderOptions;

/**
 * Streaming counterpart of {@link InMemoryYAMLDataSource}. Pulls YAML events one at a time
 * and emits flattened {@code {key, value}} rows without composing the whole document tree.
 *
 * <p>SnakeYAML's default 3&nbsp;MB code-point limit is lifted so genuinely large documents
 * can be streamed; memory stays bounded by nesting depth, not document size.</p>
 */
public class StreamingYAMLDataSource extends AbstractStreamingJacksonDataSource {

	public StreamingYAMLDataSource(String fileName) {
		super(fileName);
	}

	public StreamingYAMLDataSource(InputStream inputStream) {
		super(inputStream);
	}

	@Override
	protected JsonFactory createFactory() {
		LoaderOptions options = new LoaderOptions();
		options.setCodePointLimit(Integer.MAX_VALUE);
		return YAMLFactory.builder().loaderOptions(options).build();
	}
}
