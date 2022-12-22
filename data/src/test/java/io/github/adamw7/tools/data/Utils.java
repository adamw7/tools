package io.github.adamw7.tools.data;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

	public static String getFileName(String fileName) {
		Path resourceDirectory = Paths.get("src", "test", "resources", fileName);
		return resourceDirectory.toFile().getAbsolutePath();
	}

}
