module datamodule {
	requires org.apache.logging.log4j;
	requires org.json;
	requires java.sql;
	
	exports io.github.adamw7.tools.data.compression;
	exports io.github.adamw7.tools.data.source.db;
	exports io.github.adamw7.tools.data.source.file;
	exports io.github.adamw7.tools.data.source.interfaces;
	exports io.github.adamw7.tools.data.structure;
	exports io.github.adamw7.tools.data.uniqueness;
	exports io.github.adamw7.tools.data.network;
}