package io.github.adamw7.tools.data;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.AnnotationConsumer;

class VariableArgumentsProvider implements ArgumentsProvider, AnnotationConsumer<VariableSource> {

	private String variableName;

	@Override
	public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
		return context.getTestClass().map(this::getMethod).map(this::getValue)
				.orElseThrow(() -> new IllegalArgumentException("Failed to load test arguments for method: " + variableName));
	}

	@Override
	public void accept(VariableSource variableSource) {
		variableName = variableSource.value();
	}

	private Method getMethod(Class<?> clazz) {
		try {
			return clazz.getDeclaredMethod(variableName, null);
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Stream<Arguments> getValue(Method method) {
		Object value = null;
		try {
			value = method.invoke(null, null);
		} catch (Exception ignored) {
		}

		return value == null ? null : (Stream<Arguments>) value;
	}
}
