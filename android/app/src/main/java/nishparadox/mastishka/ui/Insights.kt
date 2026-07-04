package nishparadox.mastishka.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nishparadox.mastishka.data.Bucket
import nishparadox.mastishka.data.Granularity
import nishparadox.mastishka.data.Session
import nishparadox.mastishka.data.currentStreakDays
import nishparadox.mastishka.data.medianSeries
import nishparadox.mastishka.data.minutesPerDay
import nishparadox.mastishka.data.parseHrSeries
import nishparadox.mastishka.data.resample
import nishparadox.mastishka.data.calmnessHrMeans
import nishparadox.mastishka.data.mindBodyAgreement
import nishparadox.mastishka.data.settleFraction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * The Insights tab: quiet, mint-on-night charts hand-rolled on Canvas, following the
 * dataviz method — thin rounded marks, 2dp surface gaps, recessive hairline axes, text in
 * text tokens (never series colors), a legend only where two series exist (planned/overtime).
 */

// ---------------------------------------------------------------- tab

@Composable
fun InsightsTab(
    filtered: List<Session>,
    allSessions: List<Session>,
    from: LocalDate,
    to: LocalDate,
    granularity: Granularity,
    zone: ZoneId,
) {
    val buckets = remember(filtered, from, to, granularity) {
        nishparadox.mastishka.data.bucketMinutes(filtered, from, to, granularity, zone)
    }
    val withHr = remember(filtered) { filtered.filter { it.hasHeartRate }.sortedBy { it.startedAt } }
    val calm = remember(filtered) { filtered.filter { it.calmness > 0 }.sortedBy { it.startedAt } }
    val heat = remember(filtered) { minutesPerDay(filtered, zone) }
    val overlaySits = remember(withHr) {
        withHr.takeLast(40).filter { parseHrSeries(it.hrSeries).size >= 2 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTiles(filtered, allSessions, zone)
        if (filtered.isEmpty()) {
            Text(
                "Nothing in this range yet.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return@Column
        }
        ChartCard("Practice time", granularity.legend()) {
            BarChart(buckets, granularity)
            PlannedOvertimeLegend()
        }
        if (withHr.size >= 2) {
            ChartCard("Heart rate", "avg per sit, min–max band") {
                LineWithBand(withHr)
            }
        }
        if (overlaySits.size >= 2) {
            ChartCard("Within a sit", "each sit's heartbeat, start → end · typical sit in solid") {
                SeriesOverlay(overlaySits)
            }
        }
        if (calm.size >= 2) {
            ChartCard("Calmness", "share of rated sits in this range") {
                CalmnessHistogram(calm)
                // Mind–body concordance: per-sit, does the rating agree with the heart's
                // signal (sit bpm clearly above/below the range's median)?
                val agreement = mindBodyAgreement(filtered)
                val insight = when {
                    agreement != null ->
                        "Mind–body agreement: ${agreement.percent}% — your rating matched the " +
                            "heart's signal in ${agreement.matches} of ${agreement.total} " +
                            "clear-signal sits (baseline ~${agreement.baselineBpm} bpm)."
                    else -> calmnessHrMeans(filtered)?.let { (lowerBpm, higherBpm) ->
                        "Heart rate when rated higher: ~$higherBpm bpm · when lower: ~$lowerBpm bpm."
                    }
                }
                insight?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        ChartCard("Pattern", "minutes per day") {
            CalendarHeatmap(heat, from, to)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun Granularity.legend() = when (this) {
    Granularity.DAY -> "minutes per day"
    Granularity.WEEK -> "minutes per week"
    Granularity.MONTH -> "minutes per month"
}

// ---------------------------------------------------------------- tiles

@Composable
private fun StatTiles(filtered: List<Session>, allSessions: List<Session>, zone: ZoneId) {
    val totalMillis = filtered.sumOf { it.totalMillis }
    val hours = totalMillis / 3_600_000
    val minutes = (totalMillis % 3_600_000) / 60_000
    val streak = remember(allSessions) {
        currentStreakDays(allSessions, zone, LocalDate.now(zone))
    }
    val hrSits = filtered.filter { it.hasHeartRate }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${filtered.size}", "sits", Modifier.weight(1f))
        StatTile(if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m", "sat", Modifier.weight(1f))
        StatTile("$streak", "day streak", Modifier.weight(1f))
        StatTile(
            if (hrSits.isEmpty()) "–" else "${hrSits.map { it.hrAvg }.average().toInt()}",
            "avg bpm", Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

// ---------------------------------------------------------------- shared chrome

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PlannedOvertimeLegend() {
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(MaterialTheme.colorScheme.secondary, "planned")
        LegendDot(MaterialTheme.colorScheme.primary, "overtime")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

private fun DrawScope.hairline(color: Color, y: Float) {
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
}

private fun DrawScope.mutedLabel(
    measurer: TextMeasurer,
    text: String,
    color: Color,
    x: Float,
    y: Float,
    anchorEnd: Boolean = false,
) {
    val layout = measurer.measure(text, TextStyle(fontSize = 10.sp, color = color))
    drawText(layout, topLeft = Offset(if (anchorEnd) x - layout.size.width else x, y))
}

// ---------------------------------------------------------------- bar chart

@Composable
private fun BarChart(buckets: List<Bucket>, granularity: Granularity) {
    val planned = MaterialTheme.colorScheme.secondary
    val overtime = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val measurer = rememberTextMeasurer()
    val gap = with(LocalDensity.current) { 2.dp.toPx() }
    val labelFmt = when (granularity) {
        Granularity.DAY -> DateTimeFormatter.ofPattern("MMM d")
        Granularity.WEEK -> DateTimeFormatter.ofPattern("MMM d")
        Granularity.MONTH -> DateTimeFormatter.ofPattern("MMM")
    }

    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val labelSpace = 16.dp.toPx()
        val plotH = size.height - labelSpace
        val maxTotal = buckets.maxOfOrNull { it.totalMinutes }?.coerceAtLeast(1) ?: 1
        val n = buckets.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot - gap).coerceAtMost(28.dp.toPx()).coerceAtLeast(2f)

        hairline(hair, plotH)
        mutedLabel(measurer, "${maxTotal}m", muted, 0f, 0f)

        buckets.forEachIndexed { i, b ->
            val x = i * slot + (slot - barW) / 2
            val plannedH = plotH * b.plannedMinutes / maxTotal
            val overtimeH = plotH * b.overtimeMinutes / maxTotal
            if (plannedH > 0f) {
                drawRoundRect(
                    planned,
                    topLeft = Offset(x, plotH - plannedH),
                    size = Size(barW, plannedH),
                    cornerRadius = CornerRadius(if (overtimeH > 0f) 0f else 4.dp.toPx()),
                )
            }
            if (overtimeH > 0f) {
                // 2dp surface gap between stacked segments (dataviz spacer rule).
                drawRoundRect(
                    overtime,
                    topLeft = Offset(x, plotH - plannedH - gap - overtimeH),
                    size = Size(barW, overtimeH),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
        // Selective x labels: first and last bucket only.
        buckets.firstOrNull()?.let {
            mutedLabel(measurer, labelFmt.format(it.start), muted, 0f, plotH + 3.dp.toPx())
        }
        if (buckets.size > 1) {
            mutedLabel(
                measurer, labelFmt.format(buckets.last().start), muted,
                size.width, plotH + 3.dp.toPx(), anchorEnd = true,
            )
        }
    }
}

// ---------------------------------------------------------------- HR line + band

@Composable
private fun LineWithBand(sits: List<Session>) {
    val mint = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val measurer = rememberTextMeasurer()
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    val zone = ZoneId.systemDefault()

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelSpace = 16.dp.toPx()
        val plotH = size.height - labelSpace
        val lo = sits.minOf { it.hrMin }.coerceAtLeast(1)
        val hi = sits.maxOf { it.hrMax }.coerceAtLeast(lo + 1)
        val t0 = sits.first().startedAt
        val t1 = sits.last().startedAt.coerceAtLeast(t0 + 1)
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0) * size.width
        fun y(bpm: Int) = plotH - (bpm - lo).toFloat() / (hi - lo) * plotH

        hairline(hair, plotH)

        // min–max band
        val band = Path()
        sits.forEachIndexed { i, s ->
            if (i == 0) band.moveTo(x(s.startedAt), y(s.hrMax)) else band.lineTo(x(s.startedAt), y(s.hrMax))
        }
        sits.asReversed().forEach { s -> band.lineTo(x(s.startedAt), y(s.hrMin)) }
        band.close()
        drawPath(band, mint.copy(alpha = 0.18f))

        // avg line
        val line = Path()
        sits.forEachIndexed { i, s ->
            if (i == 0) line.moveTo(x(s.startedAt), y(s.hrAvg)) else line.lineTo(x(s.startedAt), y(s.hrAvg))
        }
        drawPath(line, mint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        mutedLabel(measurer, "$hi bpm", muted, 0f, 0f)
        mutedLabel(measurer, "$lo", muted, 0f, plotH - 14.dp.toPx())
        mutedLabel(
            measurer, fmt.format(Instant.ofEpochMilli(t0).atZone(zone).toLocalDate()),
            muted, 0f, plotH + 3.dp.toPx(),
        )
        mutedLabel(
            measurer, fmt.format(Instant.ofEpochMilli(t1).atZone(zone).toLocalDate()),
            muted, size.width, plotH + 3.dp.toPx(), anchorEnd = true,
        )
    }
}

// ---------------------------------------------------------------- per-sit overlay

@Composable
private fun SeriesOverlay(sits: List<Session>) {
    val mint = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val measurer = rememberTextMeasurer()
    val points = 60
    val series = remember(sits) { sits.map { parseHrSeries(it.hrSeries) } }
    val median = remember(series) { medianSeries(series, points) }
    val resampled = remember(series) { series.map { resample(it, points) } }
    // Where the heart typically completes its settling drop: median fraction across sits,
    // labeled in real minutes (each sit's fraction × its own duration).
    val settle = remember(sits) {
        val perSit = sits.mapNotNull { s ->
            settleFraction(parseHrSeries(s.hrSeries))?.let { f -> f to f * s.totalMillis / 60_000f }
        }
        if (perSit.size < 3) null else {
            val fractions = perSit.map { it.first }.sorted()
            val minutes = perSit.map { it.second }.sorted()
            fractions[fractions.size / 2] to minutes[minutes.size / 2]
        }
    }

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val plotH = size.height
        val all = resampled.flatten()
        val lo = all.min().coerceAtLeast(1)
        val hi = all.max().coerceAtLeast(lo + 1)
        fun y(bpm: Int) = plotH - (bpm - lo).toFloat() / (hi - lo) * plotH
        fun x(i: Int) = i.toFloat() / (points - 1) * size.width

        hairline(hair, plotH - 1f)

        for (curve in resampled) {
            val path = Path()
            curve.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, mint.copy(alpha = 0.12f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        val med = Path()
        median.forEachIndexed { i, v -> if (i == 0) med.moveTo(x(i), y(v)) else med.lineTo(x(i), y(v)) }
        drawPath(med, mint, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Settling marker: the minute mark where calm typically arrives.
        settle?.let { (fraction, minutes) ->
            val sx = fraction * size.width
            drawLine(
                muted, Offset(sx, 0f), Offset(sx, plotH),
                strokeWidth = 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            mutedLabel(measurer, "settles ≈${minutes.toInt()}m", muted, sx + 5.dp.toPx(), 0f)
        }

        mutedLabel(measurer, "$hi bpm", muted, 0f, 0f)
        mutedLabel(measurer, "${(hi + lo) / 2}", muted, 0f, plotH / 2 - 7.dp.toPx())
        mutedLabel(measurer, "$lo", muted, 0f, plotH - 16.dp.toPx())
    }
}

// ---------------------------------------------------------------- calmness shares

/** A three-bar percentage histogram of the rated sits: lower · neutral · higher (left → right,
 *  ordinal, so one hue at three strengths). No time axis — with only three levels and sparse
 *  ratings, distribution beats trend. */
@Composable
private fun CalmnessHistogram(rated: List<Session>) {
    val mint = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val hair = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val measurer = rememberTextMeasurer()

    val lower = rated.count { it.calmness == 1 }
    val neutral = rated.count { it.calmness == 2 }
    val higher = rated.count { it.calmness >= 3 }
    val total = (lower + neutral + higher).coerceAtLeast(1)
    val bars = listOf(
        Triple("lower", lower, mint.copy(alpha = 0.16f)),
        Triple("neutral", neutral, mint.copy(alpha = 0.45f)),
        Triple("higher", higher, mint),
    )

    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val labelSpace = 16.dp.toPx()
        val pctSpace = 16.dp.toPx()
        val plotH = size.height - labelSpace - pctSpace
        val maxPct = bars.maxOf { it.second } * 100f / total
        val slot = size.width / bars.size
        val barW = (slot * 0.5f).coerceAtMost(64.dp.toPx())

        hairline(hair, pctSpace + plotH)

        bars.forEachIndexed { i, (name, count, color) ->
            val pct = count * 100f / total
            val h = if (maxPct > 0f) plotH * pct / maxPct else 0f
            val x = i * slot + (slot - barW) / 2
            if (h > 0f) {
                drawRoundRect(
                    color,
                    topLeft = Offset(x, pctSpace + plotH - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            // Percentage above the bar, category name below the baseline — text tokens only.
            val pctLayout = measurer.measure(
                "${pct.toInt()}%", TextStyle(fontSize = 10.sp, color = muted)
            )
            drawText(
                pctLayout,
                topLeft = Offset(x + barW / 2 - pctLayout.size.width / 2, pctSpace + plotH - h - 15.dp.toPx()),
            )
            val nameLayout = measurer.measure(name, TextStyle(fontSize = 10.sp, color = muted))
            drawText(
                nameLayout,
                topLeft = Offset(x + barW / 2 - nameLayout.size.width / 2, pctSpace + plotH + 3.dp.toPx()),
            )
        }
    }
    Text(
        "$total rated sit${if (total == 1) "" else "s"}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
    )
}

// ---------------------------------------------------------------- calendar heatmap

@Composable
private fun CalendarHeatmap(minutes: Map<LocalDate, Int>, from: LocalDate, to: LocalDate) {
    val mint = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val measurer = rememberTextMeasurer()

    // Cap the grid at ~53 weeks so "All" doesn't build an endless canvas.
    val end = to
    val start = maxOf(from, end.minusDays(370)).with(DayOfWeek.MONDAY)
    val weeks = remember(start, end) {
        generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
    }
    val maxMinutes = (minutes.values.maxOrNull() ?: 0).coerceAtLeast(1)

    val cell = 13.dp
    val gap = 2.dp
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier
                .width((cell + gap) * weeks.size + 44.dp)
                .height((cell + gap) * 7 + 16.dp)
        ) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val topPad = 14.dp.toPx()
            val leftPad = 20.dp.toPx()
            // Weekday guide: columns are weeks, rows are days.
            for ((dow, label) in listOf(0 to "M", 2 to "W", 4 to "F")) {
                mutedLabel(measurer, label, muted, 0f, topPad + dow * (cellPx + gapPx))
            }
            var lastMonth = -1
            weeks.forEachIndexed { col, weekStart ->
                // Month label above the first week of each month.
                if (weekStart.monthValue != lastMonth) {
                    lastMonth = weekStart.monthValue
                    mutedLabel(
                        measurer,
                        DateTimeFormatter.ofPattern("MMM").format(weekStart),
                        muted, leftPad + col * (cellPx + gapPx), 0f,
                    )
                }
                for (dow in 0 until 7) {
                    val day = weekStart.plusDays(dow.toLong())
                    if (day.isBefore(from) || day.isAfter(end)) continue
                    val m = minutes[day] ?: 0
                    val color = when {
                        m == 0 -> empty
                        else -> mint.copy(alpha = 0.25f + 0.75f * (m.toFloat() / maxMinutes).coerceAtMost(1f))
                    }
                    drawRoundRect(
                        color,
                        topLeft = Offset(leftPad + col * (cellPx + gapPx), topPad + dow * (cellPx + gapPx)),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
        }
    }
    // Color key: how bright maps to minutes (brightest = the biggest day in range).
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "0m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.width(2.dp))
        Surface(color = empty, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(12.dp)) {}
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { level ->
            Surface(
                color = mint.copy(alpha = 0.25f + 0.75f * level),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.size(12.dp),
            ) {}
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "${maxMinutes}m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}
