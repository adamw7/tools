package io.github.adamw7.tools.code;

import java.util.Set;

import com.google.protobuf.GeneratedMessageV3;

public class Code {
	
	private final String outputDir;
	
	public Code() {
		outputDir = "target/generated-sources";
	}

	public void genBuilders(Set<Class<? extends GeneratedMessageV3>> allMessages) {
		for (Class<? extends GeneratedMessageV3> c : allMessages) {
			write(genBuilder(c));
		}
	}

	private void write(String genBuilder) {
		// TODO Auto-generated method stub
		
	}

	private String genBuilder(Class<? extends GeneratedMessageV3> c) {
		return null;
	}

}
