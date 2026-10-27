package montafra.beam

import kotlin.math.abs

/** Manually confirmed full reference; independent of broken Android level. */
data class FullChargeCalibration(
    val millivolts: Double,
    val wasCharging: Boolean,
    val fullCounterMicroAh: Long?,
    val whenMs: Long
)

enum class EstimateSource { COUNTER, CURRENT, CALIBRATED_VOLTAGE, VOLTAGE, UNKNOWN }
data class ChargeEstimate(val percent: Double?, val source: EstimateSource)

/**
 * Relative hardware charge-counter > integrated current > voltage-based fallback.
 * Current integration only counts uninterrupted observations; it does not estimate
 * missing background usage. Neither method guarantees correct state of charge.
 */
class ChargeTracker {
    private var lastElapsedMs: Long? = null
    private var lastCurrentUa: Long? = null
    private var integratedMah = 0.0
    private var integrationValid = false

    fun reset() {
        lastElapsedMs = null
        lastCurrentUa = null
        integratedMah = 0.0
        integrationValid = true
    }

    fun estimate(
        voltageMv: Double?, charging: Boolean, counterUaH: Long?,
        currentUa: Long?, elapsedMs: Long, capacityMah: Int,
        offsetMv: Int, full: FullChargeCalibration?
    ): ChargeEstimate {
        val voltage = VoltageEstimate.estimate(voltageMv, charging, offsetMv,
            full?.millivolts, full?.wasCharging)
        if (full == null) return ChargeEstimate(voltage, if (voltage == null)
            EstimateSource.UNKNOWN else EstimateSource.VOLTAGE)

        val prevTime = lastElapsedMs
        val prevCurrent = lastCurrentUa
        if (prevTime != null) {
            val dt = elapsedMs - prevTime
            if (dt !in 1L..30_000L ||
                !validCurrent(currentUa, charging) ||
                !validCurrent(prevCurrent, charging)) {
                integrationValid = false
            } else if (integrationValid) {
                // Android BatteryManager current sign: charging positive.
                integratedMah += (currentUa!!.toDouble() + prevCurrent!!.toDouble()) *
                    0.5 * dt / 3_600_000_000.0
            }
        }
        lastElapsedMs = elapsedMs
        lastCurrentUa = currentUa

        val capacity = capacityMah.coerceIn(1000, 10000).toDouble()
        val baseline = full.fullCounterMicroAh?.takeIf {
            it > capacity * 250 && it < capacity * 1500
        }
        val counter = counterUaH?.takeIf { it > 0 && it < capacity * 1500 }
        val deltaMah = if (baseline != null && counter != null)
            (counter - baseline).toDouble() / 1000.0 else null
        val counterSoc = deltaMah?.takeIf { it in -capacity * 1.05..capacity * .05 }
            ?.let { (100.0 + it * 100.0 / capacity).coerceIn(0.0, 100.0) }
        val tolerance = if (charging || abs(currentUa ?: 0L) > 600_000L) 30.0 else 16.0
        val counterPlausible = counterSoc != null &&
            (voltage == null || abs(counterSoc - voltage) <= tolerance)
        val stuckCounter = integrationValid && abs(integratedMah) > capacity * .015 &&
            abs(deltaMah ?: 0.0) < capacity * .003
        if (counterPlausible && !stuckCounter)
            return ChargeEstimate(counterSoc, EstimateSource.COUNTER)

        if (integrationValid && prevTime != null &&
            validCurrent(currentUa, charging) &&
            integratedMah in -capacity * 1.05..capacity * .05) {
            val p = (100.0 + integratedMah * 100.0 / capacity).coerceIn(0.0, 100.0)
            if (voltage == null || abs(p - voltage) <= tolerance)
                return ChargeEstimate(p, EstimateSource.CURRENT)
        }
        return ChargeEstimate(voltage, if (voltage == null)
            EstimateSource.UNKNOWN else EstimateSource.CALIBRATED_VOLTAGE)
    }

    private fun validCurrent(ua: Long?, charging: Boolean): Boolean {
        if (ua == null || ua == 0L || ua !in -10_000_000L..10_000_000L) return false
        return if (charging) ua > 0L else ua < 0L
    }
}
