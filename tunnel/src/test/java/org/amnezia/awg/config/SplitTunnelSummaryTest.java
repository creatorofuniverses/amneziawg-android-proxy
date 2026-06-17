package org.amnezia.awg.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitTunnelSummaryTest {
    @Test public void noneSelected_isAllApps() {
        assertTrue(SplitTunnelSummary.isAllApps(0, 0));
        assertEquals(86, SplitTunnelSummary.routedCount(0, 0, 86));
    }
    @Test public void includeMode_routesOnlySelected() {
        assertFalse(SplitTunnelSummary.isAllApps(2, 0));
        assertEquals(2, SplitTunnelSummary.routedCount(2, 0, 86));
    }
    @Test public void excludeMode_routesRemainder() {
        assertFalse(SplitTunnelSummary.isAllApps(0, 5));
        assertEquals(81, SplitTunnelSummary.routedCount(0, 5, 86));
    }
}
