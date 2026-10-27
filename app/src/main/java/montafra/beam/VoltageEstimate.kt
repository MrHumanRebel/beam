package montafra.beam

/**
 * Voltage-only approximation for 4.45 V-max Li-ion packs. These are illustrative
 * rested-voltage anchors, NOT a factory BM59 discharge curve or true state of charge.
 * It never uses Android's potentially stuck battery percentage.
 */
object VoltageEstimate {
    private data class Anchor(val mv: Double, val percent: Double)
    private val anchors = listOf(
        Anchor(3300.0, 0.0), Anchor(3550.0, 3.0), Anchor(3650.0, 10.0),
        Anchor(3750.0, 18.0), Anchor(3840.0, 32.0), Anchor(3940.0, 46.0),
        Anchor(4040.0, 60.0), Anchor(4140.0, 72.0), Anchor(4240.0, 83.0),
        Anchor(4340.0, 93.0), Anchor(4450.0, 100.0),
    )

    /** Returns null rather than reporting a misleading percentage for invalid voltage. */
    fun estimate(voltageMv: Double?, charging: Boolean, offsetMv: Int = 0): Double? {
        if (voltageMv == null || !voltageMv.isFinite() || voltageMv !in 3000.0..4600.0)
            return null
        // Charging temporarily raises terminal voltage; this is a heuristic only.
        val adjusted = voltageMv + offsetMv.coerceIn(-150, 150) - (if (charging) 75.0 else 0.0)
        if (adjusted <= anchors.first().mv) return 0.0
        if (adjusted >= anchors.last().mv) return 100.0
        val i = anchors.indexOfFirst { adjusted < it.mv }
        if (i < 1) return null
        val lo = anchors[i - 1]
        val hi = anchors[i]
        return lo.percent + (adjusted - lo.mv) *
            (hi.percent - lo.percent) / (hi.mv - lo.mv)
    }
}

/** Low-pass filter; resets on unavailable readings instead of using stale charge data. */
class VoltageEstimateFilter(private val alpha: Double = 0.25) {
    private var previous: Double? = null
    fun update(percent: Double?): Double? {
        if (percent == null || !percent.isFinite()) {
            previous = null
            return null
        }
        val value = percent.coerceIn(0.0, 100.0)
        val next = previous?.let { it + (value - it) * alpha.coerceIn(0.0, 1.0) } ?: value
        previous = next
        return next
    }
}
