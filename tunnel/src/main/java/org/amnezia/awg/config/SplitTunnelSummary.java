package org.amnezia.awg.config;

/** Pure helpers for the split-tunnel "N of M apps routed" entry-row summary. */
public final class SplitTunnelSummary {
    private SplitTunnelSummary() {}

    public static boolean isAllApps(final int includedCount, final int excludedCount) {
        return includedCount == 0 && excludedCount == 0;
    }

    public static int routedCount(final int includedCount, final int excludedCount, final int totalApps) {
        if (includedCount > 0) return includedCount;
        if (excludedCount > 0) return Math.max(0, totalApps - excludedCount);
        return totalApps;
    }
}
