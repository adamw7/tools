package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.ProxySelector;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 * and leaves the network sealed. It also pins the monitor the switch locks: the
 * private one, not {@code Switch.class}, which anything on the classpath could
 * take and hold.
 */
public class SwitchThreadSafetyTest {

	private static final int THREADS = 16;

	private static final int WAIT_SECONDS = 5;

	/**
	 * The neighbouring thread must still hold {@code Switch.class} when the
	 * assertion window closes, so that a blocked {@code off()} fails the test
	 * rather than being let through by the holder timing out. The test releases it
	 * in a {@code finally}, so this ceiling is only reached if that never runs.
	 */
	private static final int HOLD_SECONDS = 60;

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

	/**
	 * Pins the monitor {@link Switch#off()} locks. A {@code static synchronized}
	 * method would lock {@code Switch.class}, so any classpath neighbour holding
	 * that monitor could stall the kill-switch; here a thread takes it and keeps
	 * it, and {@code off()} must still complete. Opts out of the per-test timeout
	 * for the same reason as the test above.
	 */
	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	public void offDoesNotWaitOnThePubliclyLockableClassMonitor() throws InterruptedException, ExecutionException {
		Switch.off(); // engage the one-way switch, so the call under test is a no-op

		CountDownLatch monitorHeld = new CountDownLatch(1);
		CountDownLatch releaseMonitor = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(holdClassMonitorUntilReleased(monitorHeld, releaseMonitor));
			assertTrue(monitorHeld.await(WAIT_SECONDS, TimeUnit.SECONDS),
					"the neighbouring thread never took the Switch.class monitor");
			assertOffCompletesWhileClassMonitorIsHeld(pool);
		} finally {
			releaseMonitor.countDown();
			pool.shutdownNow();
		}
	}

	private Runnable holdClassMonitorUntilReleased(CountDownLatch held, CountDownLatch release) {
		return () -> {
			synchronized (Switch.class) {
				held.countDown();
				awaitRelease(release);
			}
		};
	}

	private void awaitRelease(CountDownLatch release) {
		try {
			release.await(HOLD_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertOffCompletesWhileClassMonitorIsHeld(ExecutorService pool)
			throws InterruptedException, ExecutionException {
		Callable<Boolean> off = Switch::off;
		Future<Boolean> result = pool.submit(off);
		assertFalse(offResultWithinWaitWindow(result), "off() must be a no-op once the switch is already engaged");
	}

	private Boolean offResultWithinWaitWindow(Future<Boolean> result) throws InterruptedException, ExecutionException {
		try {
			return result.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			return fail("off() blocked while another thread held the Switch.class monitor, "
					+ "so it is locking a monitor code outside Switch can take", e);
		}
	}

	private void assertNetworkStaysOff() {
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> ProxySelector.getDefault().select(URI.create("http://192.0.2.1")),
				"the switch must keep the network off after concurrent calls");
		assertEquals("The network is off", thrown.getMessage());
	}
}
