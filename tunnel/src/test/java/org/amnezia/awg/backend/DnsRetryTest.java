package org.amnezia.awg.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DnsRetryTest {
    @Test public void firstRetryIsFast_notAFlatSecond() {
        // Old behaviour slept a flat 1000ms before every retry, so a transient
        // blip cost a full second. The first backoff must be much shorter.
        assertEquals(200, DnsRetry.backoffMillis(0));
    }

    @Test public void delayGrowsExponentially() {
        assertEquals(400, DnsRetry.backoffMillis(1));
        assertEquals(800, DnsRetry.backoffMillis(2));
        assertEquals(1600, DnsRetry.backoffMillis(3));
    }

    @Test public void delayIsCapped() {
        assertEquals(DnsRetry.MAX_DELAY_MS, DnsRetry.backoffMillis(10));
    }

    @Test public void fewerAttemptsThanOldTenRetryLoop() {
        assertTrue("retry budget should be tighter than the old 10", DnsRetry.MAX_ATTEMPTS < 10);
    }

    @Test public void worstCaseBlockingIsWellUnderOldNineSeconds() {
        long total = 0;
        for (int i = 0; i < DnsRetry.MAX_ATTEMPTS - 1; i++)
            total += DnsRetry.backoffMillis(i);
        assertTrue("worst-case total sleep " + total + "ms should be < old ~9000ms", total < 9000);
    }
}
