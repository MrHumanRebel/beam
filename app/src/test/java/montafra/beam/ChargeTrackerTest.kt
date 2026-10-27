package montafra.beam

import org.junit.Assert.*
import org.junit.Test

class ChargeTrackerTest {
    private val full = FullChargeCalibration(4450.0, false, 4_800_000L, 1000L)

    @Test fun manualFullAnchor() {
        assertEquals(100.0, VoltageEstimate.estimate(4340.0, false,
            fullVoltageMv = 4340.0)!!, .001)
        assertEquals(100.0, VoltageEstimate.estimate(4340.0, true,
            fullVoltageMv = 4340.0, fullWasCharging = true)!!, .001)
    }
    @Test fun configuredCapacityIsApplied() {
        val a = ChargeTracker().estimate(4180.0, false, 3_800_000L,
            null, 1000L, 5000, 0, full)
        assertEquals(80.0, a.percent!!, .001)
        assertEquals(EstimateSource.COUNTER, a.source)
        val b = ChargeTracker().estimate(4180.0, false, 3_800_000L,
            null, 1000L, 4000, 0, full)
        assertEquals(75.0, b.percent!!, .001)
    }
    @Test fun implausibleCounterFallsBack() {
        val result = ChargeTracker().estimate(4400.0, false, 100_000L,
            null, 1000L, 5000, 0, full)
        assertEquals(EstimateSource.CALIBRATED_VOLTAGE, result.source)
        assertTrue(result.percent!! > 90.0)
    }
    @Test fun rejectsGapsInCurrentReadings() {
        val tracker = ChargeTracker()
        val noCounter = full.copy(fullCounterMicroAh = null)
        tracker.reset()
        tracker.estimate(4450.0, false, null, -1_000_000L, 0L, 5000, 0, noCounter)
        val a = tracker.estimate(4450.0, false, null, -1_000_000L,
            3600L, 5000, 0, noCounter)
        assertEquals(EstimateSource.CURRENT, a.source)
        assertEquals(99.98, a.percent!!, .01)
        val b = tracker.estimate(4230.0, false, null, -1_000_000L,
            35_000L, 5000, 0, noCounter)
        assertEquals(EstimateSource.CALIBRATED_VOLTAGE, b.source)
    }
}
