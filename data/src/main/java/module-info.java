module io.github.adamw7.tools.data {
	requires tools.mcp.common;
	requires org.apache.logging.log4j;
	requires java.sql;
	requires duckdb.jdbc;
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires com.fasterxml.jackson.dataformat.yaml;
	requires org.yaml.snakeyaml;
	requires spring.beans;
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

	// Spring reflects into the MCP configuration/beans in this package
	// (component scan, @Value injection); no other package needs deep
	// reflection, so open just this one instead of the whole module.
	opens io.github.adamw7.tools.data.uniqueness.mcp;
}
