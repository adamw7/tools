open module datamodule {
	requires org.apache.logging.log4j;
	requires java.sql;
	requires duckdb.jdbc;
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.dataformat.yaml;
	requires org.yaml.snakeyaml;
	requires spring.context;
	requires spring.boot.autoconfigure;
	requires spring.boot;

	exports io.github.adamw7.tools.data.compression;
	exports io.github.adamw7.tools.data.source.db;
	exports io.github.adamw7.tools.data.source.file;
	exports io.github.adamw7.tools.data.source.interfaces;
	exports io.github.adamw7.tools.data.structure;
	exports io.github.adamw7.tools.data.uniqueness;
	exports io.github.adamw7.tools.data.network;
}
