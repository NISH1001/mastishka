package nishparadox.mastishka.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class InsightsTest {

    private val zone = ZoneOffset.UTC

    private fun sit(date: String, hour: Int = 7, plannedMin: Long = 30, totalMin: Long = 30) = Session(
        startedAt = LocalDateTime.of(LocalDate.parse(date), LocalTime.of(hour, 0))
            .toInstant(ZoneOffset.UTC).toEpochMilli(),
        plannedMillis = plannedMin * 60_000,
        totalMillis = totalMin * 60_000,
        calmness = 0,
        people = emptyList(),
        notes = "",
    )

    // ---------- bucketMinutes ----------

    @Test
    fun `daily buckets cover the range including empty days`() {
        val sessions = listOf(sit("2026-07-01"), sit("2026-07-03", plannedMin = 10, totalMin = 15))
        val buckets = bucketMinutes(
            sessions, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"),
            Granularity.DAY, zone,
        )
        assertEquals(3, buckets.size)
        assertEquals(30, buckets[0].plannedMinutes)
        assertEquals(0, buckets[0].overtimeMinutes)
        assertEquals(0, buckets[1].plannedMinutes + buckets[1].overtimeMinutes)
        assertEquals(10, buckets[2].plannedMinutes)
        assertEquals(5, buckets[2].overtimeMinutes)
    }

    @Test
    fun `a sit shorter than planned counts only actual time`() {
        val buckets = bucketMinutes(
            listOf(sit("2026-07-01", plannedMin = 30, totalMin = 20)),
            LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"),
            Granularity.DAY, zone,
        )
        assertEquals(20, buckets[0].plannedMinutes)
        assertEquals(0, buckets[0].overtimeMinutes)
    }

    @Test
    fun `weekly buckets group by ISO week starting Monday`() {
        // 2026-07-05 is a Sunday, 2026-07-06 a Monday: adjacent days, different weeks.
        val sessions = listOf(sit("2026-07-05"), sit("2026-07-06"))
        val buckets = bucketMinutes(
            sessions, LocalDate.parse("2026-07-05"), LocalDate.parse("2026-07-06"),
            Granularity.WEEK, zone,
        )
        assertEquals(2, buckets.size)
        assertEquals(LocalDate.parse("2026-06-29"), buckets[0].start) // that week's Monday
        assertEquals(LocalDate.parse("2026-07-06"), buckets[1].start)
    }

    @Test
    fun `monthly buckets group by month`() {
        val sessions = listOf(sit("2026-06-30"), sit("2026-07-01"))
        val buckets = bucketMinutes(
            sessions, LocalDate.parse("2026-06-30"), LocalDate.parse("2026-07-01"),
            Granularity.MONTH, zone,
        )
        assertEquals(2, buckets.size)
        assertEquals(LocalDate.parse("2026-06-01"), buckets[0].start)
        assertEquals(LocalDate.parse("2026-07-01"), buckets[1].start)
    }

    // ---------- per-day totals & average ----------

    @Test
    fun `dailyTotals drops leading empty days but keeps mid-range zeros`() {
        // Range opens 07-01 but the first sit is 07-03; a gap on 07-04 stays as a zero day.
        val sessions = listOf(sit("2026-07-03", totalMin = 40), sit("2026-07-05", totalMin = 20))
        val daily = dailyTotals(
            sessions, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-05"), zone,
        )
        // 07-03, 07-04(0), 07-05 — the empty 07-01/07-02 lead is trimmed.
        assertEquals(3, daily.size)
        assertEquals(LocalDate.parse("2026-07-03"), daily.first().start)
        assertEquals(listOf(40, 0, 20), daily.map { it.totalMinutes })
    }

    @Test
    fun `perDayAverage divides total by days since the first sit, not by sits`() {
        // Two sits on one day + one the next = 90 min over 2 days = 45/day (not 30/sit).
        val sessions = listOf(
            sit("2026-07-03", hour = 6, totalMin = 30),
            sit("2026-07-03", hour = 8, totalMin = 30),
            sit("2026-07-04", totalMin = 30),
        )
        val daily = dailyTotals(
            sessions, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-04"), zone,
        )
        assertEquals(45, perDayAverage(daily))
    }

    @Test
    fun `perDayAverage is zero for an empty range`() {
        val daily = dailyTotals(
            emptyList(), LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-07"), zone,
        )
        assertEquals(0, perDayAverage(daily))
    }

    // ---------- streak ----------

    @Test
    fun `streak counts consecutive days ending today`() {
        val sessions = listOf(sit("2026-07-02"), sit("2026-07-03"), sit("2026-07-04"))
        assertEquals(3, currentStreakDays(sessions, zone, LocalDate.parse("2026-07-04")))
    }

    @Test
    fun `streak still alive if last sit was yesterday`() {
        val sessions = listOf(sit("2026-07-02"), sit("2026-07-03"))
        assertEquals(2, currentStreakDays(sessions, zone, LocalDate.parse("2026-07-04")))
    }

    @Test
    fun `streak broken by a missed day is zero`() {
        val sessions = listOf(sit("2026-07-01"))
        assertEquals(0, currentStreakDays(sessions, zone, LocalDate.parse("2026-07-04")))
    }

    @Test
    fun `two sits one day count once in a streak`() {
        val sessions = listOf(sit("2026-07-04", hour = 6), sit("2026-07-04", hour = 8))
        assertEquals(1, currentStreakDays(sessions, zone, LocalDate.parse("2026-07-04")))
    }

    // ---------- series helpers ----------

    @Test
    fun `parseHrSeries reads ints and skips junk`() {
        assertEquals(listOf(60, 62, 64), parseHrSeries("60,62,64"))
        assertEquals(listOf(60, 64), parseHrSeries("60,,x,64"))
        assertEquals(emptyList<Int>(), parseHrSeries(""))
    }

    @Test
    fun `resample stretches and shrinks to the target size`() {
        assertEquals(listOf(1, 1, 2, 2), resample(listOf(1, 2), 4))
        assertEquals(4, resample(listOf(1, 2, 3, 4, 5, 6, 7, 8), 4).size)
        assertEquals(emptyList<Int>(), resample(emptyList(), 4))
    }

    @Test
    fun `medianSeries is the pointwise median of resampled curves`() {
        val median = medianSeries(
            listOf(listOf(10, 20), listOf(20, 30), listOf(30, 40)),
            points = 2,
        )
        assertEquals(listOf(20, 30), median)
    }

    @Test
    fun `rollingMean smooths with a trailing window`() {
        assertEquals(listOf(1f, 1.5f, 2.5f, 3.5f), rollingMean(listOf(1, 2, 3, 4), window = 2))
    }

    // ---------- settling & calmness↔HR ----------

    @Test
    fun `settleFraction finds where most of the drop is done`() {
        // 80 → 60: 80% of the drop (to 64) is first reached at index 4 of 0..5.
        assertEquals(0.8f, settleFraction(listOf(80, 76, 72, 68, 64, 60))!!, 0.01f)
    }

    @Test
    fun `settleFraction is null for flat or tiny series`() {
        assertEquals(null, settleFraction(listOf(70, 70, 70, 70)))
        assertEquals(null, settleFraction(listOf(70)))
    }

    @Test
    fun `mindBodyAgreement counts per-sit matches against the bpm median`() {
        val sessions = listOf(
            // Baseline median bpm over HR sits: 60,70,80 → 70.
            sit("2026-07-01").copy(calmness = 3, hrAvg = 60), // body calm + rated higher → match
            sit("2026-07-02").copy(calmness = 1, hrAvg = 80), // body tense + rated lower → match
            sit("2026-07-03").copy(calmness = 1, hrAvg = 60), // body calm + rated lower → mismatch
            sit("2026-07-04").copy(calmness = 3, hrAvg = 70), // inside ±2 dead zone → skipped
            sit("2026-07-05").copy(calmness = 2, hrAvg = 80), // neutral → no direction, skipped
            sit("2026-07-06").copy(calmness = 3, hrAvg = 0),  // no heart data → skipped
        )
        val a = mindBodyAgreement(sessions)!!
        assertEquals(2, a.matches)
        assertEquals(3, a.total)
        assertEquals(70, a.baselineBpm)
    }

    @Test
    fun `mindBodyAgreement is null without enough clear-signal sits`() {
        val sessions = listOf(
            sit("2026-07-01").copy(calmness = 3, hrAvg = 60),
            sit("2026-07-02").copy(calmness = 1, hrAvg = 80),
        )
        assertEquals(null, mindBodyAgreement(sessions))
    }

    @Test
    fun `calmnessHrMeans averages bpm for lower-rated vs higher-rated sits`() {
        val sessions = listOf(
            sit("2026-07-01").copy(calmness = 1, hrAvg = 70),
            sit("2026-07-02").copy(calmness = 1, hrAvg = 68),
            sit("2026-07-03").copy(calmness = 3, hrAvg = 62),
            sit("2026-07-04").copy(calmness = 3, hrAvg = 64),
        )
        assertEquals(69 to 63, calmnessHrMeans(sessions))
    }

    @Test
    fun `calmnessHrMeans needs both groups`() {
        val sessions = listOf(sit("2026-07-01").copy(calmness = 3, hrAvg = 62))
        assertEquals(null, calmnessHrMeans(sessions))
    }

    // ---------- heatmap ----------

    @Test
    fun `minutesPerDay sums a day's sits`() {
        val sessions = listOf(
            sit("2026-07-04", hour = 6, totalMin = 30),
            sit("2026-07-04", hour = 8, totalMin = 15),
            sit("2026-07-03", totalMin = 60),
        )
        val map = minutesPerDay(sessions, zone)
        assertEquals(45, map[LocalDate.parse("2026-07-04")])
        assertEquals(60, map[LocalDate.parse("2026-07-03")])
    }
}
