package io.github.adamw7.tools.data.network;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Engages the {@link Switch} network kill-switch for the annotated test class, so
 * the test can never open an outbound connection. Add it to any JUnit 5 test that
 * must run with the network off:
 *
 * <pre>{@code
 * @NetworkOff
 * class MyDataSourceTest {
 *     ...
 * }
 * }</pre>
 *
 * The annotation is a thin composition over {@link NetworkOffExtension}: applying
 * it registers the extension, which turns the network off once before the class's
 * tests run. Unlike the module-wide auto-detected registration — gated on the
 * {@code tools.test.network.off} system property so failsafe's {@code *IT} tests
 * keep real network — an explicit {@code @NetworkOff} engages the kill-switch
 * unconditionally, so it also takes effect when a developer runs the single test
 * from an IDE without that property set.
 *
 * @see NetworkOffExtension
 * @see Switch
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(NetworkOffExtension.class)
public @interface NetworkOff {
}
