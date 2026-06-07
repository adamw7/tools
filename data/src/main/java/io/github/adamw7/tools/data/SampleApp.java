package io.github.adamw7.tools.data;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.adamw7.tools.data.source.file.InMemoryCSVDataSource;
import io.github.adamw7.tools.data.source.interfaces.InMemoryDataSource;
import io.github.adamw7.tools.data.uniqueness.AbstractUniqueness;
import io.github.adamw7.tools.data.uniqueness.InMemoryUniquenessCheck;
import io.github.adamw7.tools.data.uniqueness.Result;

public class SampleApp {
	

	private final static Logger log = LogManager.getLogger(SampleApp.class.getName());

	public static void main(String[] args) {
		checkArgs(args);
		String fileName = args[0];
		String columnName = args[1];
		executeUniquenessCheck(fileName, columnName);
	}

	private static void executeUniquenessCheck(String fileName, String columnName) {
		try {
			InMemoryDataSource source = new InMemoryCSVDataSource(fileName, 1);
			AbstractUniqueness check = new InMemoryUniquenessCheck();
			
			source.readAll();
			
			check.setDataSource(source);
			print(check.exec(columnName), columnName);			
		} catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void print(Result result, String column) {
		if (result.isUnique()) {
            log.info("{} is unique", column);
		} else {
            log.info("{} is NOT unique", column);
		}
	}

	private static void checkArgs(String[] args) {
		if (args == null || args.length <2) {
			throw new IllegalArgumentException("Input format should be: filename columnname");
		}
	}
}
