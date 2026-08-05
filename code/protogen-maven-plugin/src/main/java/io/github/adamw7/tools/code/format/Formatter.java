package io.github.adamw7.tools.code.format;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;

public class Formatter implements FormatterIfc {

	@Override
	public String format(String code) {
		CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);
		return Documents.edited(code,
				document -> codeFormatter.format(CodeFormatter.K_COMPILATION_UNIT, code, 0, code.length(), 0, null));
	}
}
