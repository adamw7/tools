package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ProxySelector;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies the thread-safety guarantee documented for {@link Switch}: that
 * {@link Switch#off()} is safe to call concurrently and idempotent. The one-way,
 * process-global flag is already off by the time any unit test runs (the
 * {@link NetworkOffExtension} engages it first), so this test cannot observe the
 * one-time {@code true} return. Instead it engages the switch itself and then
 * hammers {@code off()} from many threads released together, proving that every
 * concurrent call is a consistent, exception-free no-op that returns {@code false}
 * and leaves the network sealed.
 */
public class SwitchThreadSafetyTest {

	private static final int THREADS = 16;

	/**
	 * Opts out of the 900 ms per-test timeout: spinning up and joining a pool of
	 * threads to force real contention is heavier than a plain unit test, though it
	 * still completes in well under a second.
	 */
	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void concurrentOffCallsAreSafeAndIdempotent() throws InterruptedException, ExecutionException {
		Switch.off(); // engage the one-way switch before the concurrent burst

		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			CyclicBarrier startLine = new CyclicBarrier(THREADS);
			List<Future<Boolean>> results = submitConcurrentOffCalls(pool, startLine);
			assertEveryCallIsANoOp(results);
		} finally {
			pool.shutdownNow();
		}

		assertNetworkStaysOff();
	}

	private List<Future<Boolean>> submitConcurrentOffCalls(ExecutorService pool, CyclicBarrier startLine) {
		List<Future<Boolean>> results = new ArrayList<>(THREADS);
		for (int i = 0; i < THREADS; ++i) {
			results.add(pool.submit(offAfterBarrier(startLine)));
		}
		return results;
	}

	private Callable<Boolean> offAfterBarrier(CyclicBarrier startLine) {
		return () -> {
			startLine.await();
			return Switch.off();
		};
	}

	private void assertEveryCallIsANoOp(List<Future<Boolean>> results)
			throws InterruptedException, ExecutionException {
		for (Future<Boolean> result : results) {
			assertFalse(result.get(), "off() must be a no-op once the switch is already engaged");
		}
	}

	private void assertNetworkStaysOff() {
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> ProxySelector.getDefault().select(URI.create("http://192.0.2.1")),
				"the switch must keep the network off after concurrent calls");
		assertEquals("The network is off", thrown.getMessage());
	}
}
