package org.amnezia.awg.backend;

/**
 * Backoff schedule for the connect-time DNS pre-resolution loop in {@link GoBackend}.
 *
 * <p>The old loop slept a flat 1000ms before each of up to 10 retries, so a peer hostname
 * that was slow to resolve on a degraded network could block the connect for ~9 seconds with
 * no feedback. This replaces that with a tighter exponential backoff: quick first retries
 * recover from transient blips fast, and the total worst-case wait is bounded well under the
 * old budget.
 */
final class DnsRetry {
    static final int MAX_ATTEMPTS = 5;
    static final long BASE_DELAY_MS = 200;
    static final long MAX_DELAY_MS = 2000;

    private DnsRetry() {}

    /** Milliseconds to sleep after the {@code attempt}-th (0-based) failed resolution pass. */
    static long backoffMillis(final int attempt) {
        return Math.min(BASE_DELAY_MS << attempt, MAX_DELAY_MS);
    }
}
