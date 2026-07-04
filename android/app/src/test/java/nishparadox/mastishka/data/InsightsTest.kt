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
    fun `calmnessHrGap compares higher-rated and lower-rated sits`() {
        val sessions = listOf(
            sit("2026-07-01").copy(calmness = 1, hrAvg = 70),
            sit("2026-07-02").copy(calmness = 1, hrAvg = 68),
            sit("2026-07-03").copy(calmness = 3, hrAvg = 62),
            sit("2026-07-04").copy(calmness = 3, hrAvg = 64),
        )
        assertEquals(6, calmnessHrGap(sessions))
    }

    @Test
    fun `calmnessHrGap needs both groups`() {
        val sessions = listOf(sit("2026-07-01").copy(calmness = 3, hrAvg = 62))
        assertEquals(null, calmnessHrGap(sessions))
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
