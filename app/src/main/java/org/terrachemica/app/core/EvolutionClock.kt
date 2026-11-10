package org.terrachemica.app.core

import kotlin.math.abs

/**
 * Two time layers:
 * - SCIENTIFIC_DAILY is exact: 1 real second advances 1 simulated day.
 * - AUTO_JOURNEY compresses only the long gaps between reference milestones.
 *   It is a presentation clock, not a claim that every skipped day was numerically solved.
 */
enum class ClockMode { AUTO_JOURNEY, SCIENTIFIC_DAILY }

data class TimelineMilestone(
    val ageSinceFormationMa: Double,
    val title: String,
    val description: String
)

data class ClockState(
    val ageSinceFormationMa: Double,
    val isPlaying: Boolean,
    val mode: ClockMode,
    val currentMilestone: TimelineMilestone?,
    val waitingAtMilestone: Boolean
)

class EvolutionClock(
    initialAgeSinceFormationMa: Double = 0.0,
    initialMode: ClockMode = ClockMode.AUTO_JOURNEY,
    initialPlaying: Boolean = true
) {
    companion object {
    const val EARTH_AGE_MA = 4540.0
    private const val DAYS_PER_YEAR = 365.2425
    private const val AUTO_TRAVEL_SECONDS_PER_SEGMENT = 150.0
    private const val MILESTONE_HOLD_SECONDS = 12.0
    private const val EPSILON_MA = 1e-7

    val milestones = listOf(
        TimelineMilestone(0.0, "Pembentukan Bumi", "Titik awal Earth Evolution; kondisi Hadean awal belum seluruhnya terkalibrasi."),
        TimelineMilestone(140.0, "Referensi spektrum 4,4 Ga", "Titik spektrum Matahari VPL pada 4,4 miliar tahun lalu."),
        TimelineMilestone(509.0, "Hadean → Archaean", "Batas eon geologi sekitar 4,031 Ga sebelum sekarang."),
        TimelineMilestone(740.0, "Archaean of Arus · 3,8 Ga", "Titik jangkar data Archaean dan spektrum Matahari 3,8 Ga."),
        TimelineMilestone(2040.0, "Awal Proterozoic", "Batas eon sekitar 2,5 Ga sebelum sekarang."),
        TimelineMilestone(2140.0, "Referensi spektrum 2,4 Ga", "Titik spektrum Matahari VPL pada 2,4 miliar tahun lalu."),
        TimelineMilestone(4001.2, "Awal Phanerozoic", "Batas eon sekitar 538,8 juta tahun sebelum sekarang."),
        TimelineMilestone(4540.0, "Bumi modern", "Ujung perjalanan pada umur Bumi sekitar 4,54 miliar tahun.")
    )
    }

    var ageSinceFormationMa: Double = initialAgeSinceFormationMa.coerceIn(0.0, EARTH_AGE_MA)
        private set
    var mode: ClockMode = initialMode
        private set
    var isPlaying: Boolean = initialPlaying && ageSinceFormationMa < EARTH_AGE_MA
        private set

    private var lastTickNanos: Long? = null
    private var holdUntilNanos: Long? = null
    private var activeMilestone: TimelineMilestone? = null
    private var autoSegmentStartAgeMa: Double? = null
    private var autoSegmentTargetMa: Double? = null
    private var autoTravelElapsedSeconds: Double = 0.0

    fun setPlaying(playing: Boolean) {
        isPlaying = playing && ageSinceFormationMa < EARTH_AGE_MA
        lastTickNanos = null
    }

    fun togglePlaying() = setPlaying(!isPlaying)

    fun setMode(newMode: ClockMode) {
        mode = newMode
        holdUntilNanos = null
        activeMilestone = null
        resetAutoSegment()
        lastTickNanos = null
    }

    fun toggleMode() {
        setMode(if (mode == ClockMode.AUTO_JOURNEY) ClockMode.SCIENTIFIC_DAILY else ClockMode.AUTO_JOURNEY)
    }

    fun restoreAge(ageMa: Double) {
        ageSinceFormationMa = ageMa.coerceIn(0.0, EARTH_AGE_MA)
        if (ageSinceFormationMa >= EARTH_AGE_MA) isPlaying = false
        holdUntilNanos = null
        activeMilestone = null
        resetAutoSegment()
        lastTickNanos = null
    }

    fun advance(nowNanos: Long): ClockState {
        val previous = lastTickNanos
        lastTickNanos = nowNanos
        if (!isPlaying || previous == null) return state()

        val deltaSeconds = (nowNanos - previous).coerceAtLeast(0L) / 1_000_000_000.0
        if (deltaSeconds <= 0.0) return state()

        when (mode) {
            ClockMode.SCIENTIFIC_DAILY -> {
                // Exactly one simulated day for each real second.
                ageSinceFormationMa += deltaSeconds / (DAYS_PER_YEAR * 1_000_000.0)
            }
            ClockMode.AUTO_JOURNEY -> advanceAuto(deltaSeconds, nowNanos)
        }

        if (ageSinceFormationMa >= EARTH_AGE_MA) {
            ageSinceFormationMa = EARTH_AGE_MA
            isPlaying = false
        }
        return state()
    }

    private fun advanceAuto(deltaSeconds: Double, nowNanos: Long) {
        var travelDeltaSeconds = deltaSeconds
        val holdEnd = holdUntilNanos
        if (holdEnd != null) {
            if (nowNanos < holdEnd) return
            // If a UI tick lands after the hold, carry only the time beyond the hold
            // into the next segment rather than counting the waiting interval as travel.
            travelDeltaSeconds = (nowNanos - holdEnd).coerceAtLeast(0L) / 1_000_000_000.0
            holdUntilNanos = null
            activeMilestone = null
            resetAutoSegment()
        }

        val next = milestones.firstOrNull { it.ageSinceFormationMa > ageSinceFormationMa + EPSILON_MA }
        if (next == null) {
            ageSinceFormationMa = EARTH_AGE_MA
            isPlaying = false
            resetAutoSegment()
            return
        }

        if (autoSegmentTargetMa == null || abs(autoSegmentTargetMa!! - next.ageSinceFormationMa) > EPSILON_MA) {
            autoSegmentStartAgeMa = ageSinceFormationMa
            autoSegmentTargetMa = next.ageSinceFormationMa
            autoTravelElapsedSeconds = 0.0
        }
        val startAge = autoSegmentStartAgeMa ?: ageSinceFormationMa
        autoTravelElapsedSeconds += travelDeltaSeconds
        val fraction = (autoTravelElapsedSeconds / AUTO_TRAVEL_SECONDS_PER_SEGMENT).coerceIn(0.0, 1.0)
        ageSinceFormationMa = startAge + (next.ageSinceFormationMa - startAge) * fraction

        if (fraction >= 1.0) {
            ageSinceFormationMa = next.ageSinceFormationMa
            activeMilestone = next
            holdUntilNanos = nowNanos + (MILESTONE_HOLD_SECONDS * 1_000_000_000L).toLong()
        }
    }

    private fun resetAutoSegment() {
        autoSegmentStartAgeMa = null
        autoSegmentTargetMa = null
        autoTravelElapsedSeconds = 0.0
    }

    private fun state(): ClockState {
        val current = activeMilestone ?: milestones.lastOrNull {
            it.ageSinceFormationMa <= ageSinceFormationMa + EPSILON_MA
        }
        return ClockState(
            ageSinceFormationMa = ageSinceFormationMa,
            isPlaying = isPlaying,
            mode = mode,
            currentMilestone = current,
            waitingAtMilestone = holdUntilNanos != null
        )
    }
}
