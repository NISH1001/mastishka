package nishparadox.mastishka.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure aggregation over sits for the History → Insights charts. Everything takes an explicit
 * [ZoneId] (and, where "now" matters, an explicit today) so it's deterministic under JVM tests.
 */

enum class Granularity { DAY, WEEK, MONTH }

/** One bar of the practice-time chart. Practice time = actual time sat, split into the
 *  planned portion and the overtime beyond it. */
data class Bucket(val start: LocalDate, val plannedMinutes: Int, val overtimeMinutes: Int) {
    val totalMinutes: Int get() = plannedMinutes + overtimeMinutes
}

private fun Session.day(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

private fun LocalDate.bucketStart(granularity: Granularity): LocalDate = when (granularity) {
    Granularity.DAY -> this
    Granularity.WEEK -> with(DayOfWeek.MONDAY)
    Granularity.MONTH -> withDayOfMonth(1)
}

private fun LocalDate.nextBucket(granularity: Granularity): LocalDate = when (granularity) {
    Granularity.DAY -> plusDays(1)
    Granularity.WEEK -> plusWeeks(1)
    Granularity.MONTH -> plusMonths(1)
}

/** Continuous buckets covering [from]..[to] (inclusive), zeros where nothing was sat. */
fun bucketMinutes(
    sessions: List<Session>,
    from: LocalDate,
    to: LocalDate,
    granularity: Granularity,
    zone: ZoneId,
): List<Bucket> {
    val planned = mutableMapOf<LocalDate, Long>()
    val overtime = mutableMapOf<LocalDate, Long>()
    for (s in sessions) {
        val key = s.day(zone).bucketStart(granularity)
        planned.merge(key, minOf(s.totalMillis, s.plannedMillis), Long::plus)
        overtime.merge(key, (s.totalMillis - s.plannedMillis).coerceAtLeast(0), Long::plus)
    }
    val buckets = mutableListOf<Bucket>()
    var cursor = from.bucketStart(granularity)
    val last = to.bucketStart(granularity)
    while (!cursor.isAfter(last)) {
        buckets.add(
            Bucket(
                start = cursor,
                plannedMinutes = ((planned[cursor] ?: 0L) / 60_000L).toInt(),
                overtimeMinutes = ((overtime[cursor] ?: 0L) / 60_000L).toInt(),
            )
        )
        cursor = cursor.nextBucket(granularity)
    }
    return buckets
}

/** Consecutive days sat, counting back from [today] — still alive if the last sit was
 *  yesterday (today's sit may simply not have happened yet). */
fun currentStreakDays(sessions: List<Session>, zone: ZoneId, today: LocalDate): Int {
    val days = sessions.map { it.day(zone) }.toHashSet()
    var cursor = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (cursor in days) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** Total minutes sat per day (heatmap cells). */
fun minutesPerDay(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Int> {
    val minutes = mutableMapOf<LocalDate, Long>()
    for (s in sessions) minutes.merge(s.day(zone), s.totalMillis, Long::plus)
    return minutes.mapValues { (it.value / 60_000L).toInt() }
}

/** The stored heart-rate series: comma-separated bpm ints; junk tokens skipped. */
fun parseHrSeries(csv: String): List<Int> =
    csv.split(",").mapNotNull { it.trim().toIntOrNull() }

/** Resample to exactly [points] values by averaging equal slices (nearest for stretches). */
fun resample(values: List<Int>, points: Int): List<Int> {
    if (values.isEmpty() || points <= 0) return emptyList()
    return (0 until points).map { i ->
        val from = i * values.size / points
        val to = ((i + 1) * values.size / points).coerceAtMost(values.size)
        if (to > from) values.subList(from, to).average().toInt()
        else values[from.coerceIn(0, values.size - 1)]
    }
}

/** Pointwise median across curves, each resampled to [points] — the "typical sit" line. */
fun medianSeries(series: List<List<Int>>, points: Int): List<Int> {
    val resampled = series.filter { it.isNotEmpty() }.map { resample(it, points) }
    if (resampled.isEmpty()) return emptyList()
    return (0 until points).map { i ->
        val column = resampled.map { it[i] }.sorted()
        column[column.size / 2]
    }
}

/** Trailing-window mean (window includes the current point; shorter at the start). */
fun rollingMean(values: List<Int>, window: Int): List<Float> =
    values.indices.map { i ->
        val from = (i - window + 1).coerceAtLeast(0)
        (from..i).sumOf { values[it] }.toFloat() / (i - from + 1)
    }

/**
 * Fraction (0..1) through a within-sit heart series where ~80% of the settling drop
 * (start bpm → minimum bpm) is first reached — "when the heart finds its calm".
 * Null when there's no meaningful drop to settle through.
 */
fun settleFraction(series: List<Int>): Float? {
    if (series.size < 2) return null
    val start = series.first()
    val floor = series.min()
    if (start - floor < 5) return null
    val target = floor + 0.2f * (start - floor)
    val index = series.indexOfFirst { it <= target }
    if (index <= 0) return null
    return index.toFloat() / (series.size - 1)
}

/**
 * How many bpm lower the higher-rated sits run vs the lower-rated ones (positive = calmer
 * ratings coincide with a slower heart). Null unless both groups have heart data.
 */
fun calmnessHrGap(sessions: List<Session>): Int? {
    val rated = sessions.filter { it.calmness > 0 && it.hasHeartRate }
    val higher = rated.filter { it.calmness >= 3 }
    val lower = rated.filter { it.calmness == 1 }
    if (higher.isEmpty() || lower.isEmpty()) return null
    return (lower.map { it.hrAvg }.average() - higher.map { it.hrAvg }.average()).toInt()
}
