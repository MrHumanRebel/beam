package montafra.beam

import org.junit.Assert.*
import org.junit.Test

class VoltageEstimateTest {
    @Test fun boundsAndMiddle() {
        assertEquals(0.0, VoltageEstimate.estimate(3300.0, false)!!, 0.01)
        assertEquals(100.0, VoltageEstimate.estimate(4450.0, false)!!, 0.01)
        assertEquals(77.5, VoltageEstimate.estimate(4190.0, false)!!, 0.01)
    }
    @Test fun invalidVoltageIsUnknown() {
        assertNull(VoltageEstimate.estimate(null, false))
        assertNull(VoltageEstimate.estimate(Double.NaN, false))
        assertNull(VoltageEstimate.estimate(0.0, false))
        assertNull(VoltageEstimate.estimate(4800.0, false))
    }
    @Test fun estimateIncreasesWithVoltage() {
        var prev = -1.0
        for (mv in 3000..4600 step 10) {
            val now = VoltageEstimate.estimate(mv.toDouble(), false)!!
            assertTrue(now >= prev)
            assertTrue(now in 0.0..100.0)
            prev = now
        }
    }
    @Test fun chargingAndOffsetCorrection() {
        val v = VoltageEstimate.estimate(4231.0, false)!!
        assertTrue(VoltageEstimate.estimate(4231.0, true)!! < v)
        assertTrue(VoltageEstimate.estimate(4231.0, false, 150)!! > v)
        assertEquals(VoltageEstimate.estimate(4231.0, false, 150)!!,
            VoltageEstimate.estimate(4231.0, false, 1000)!!, 0.01)
    }
    @Test fun filterResetsUnavailable() {
        val filter = VoltageEstimateFilter()
        assertEquals(80.0, filter.update(80.0)!!, 0.01)
        assertEquals(85.0, filter.update(100.0)!!, 0.01)
        assertNull(filter.update(null))
        assertEquals(10.0, filter.update(10.0)!!, 0.01)
    }
}
