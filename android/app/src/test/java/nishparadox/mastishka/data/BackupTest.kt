package nishparadox.mastishka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val sits = listOf(
        Session(
            startedAt = 1751500000000L,
            plannedMillis = 600_000L,
            totalMillis = 694_000L,
            calmness = 4,
            people = listOf("ma", "buwa"),
            notes = "calm morning",
            type = "Anapana",
            hrAvg = 62, hrMin = 55, hrMax = 71,
            hrSeries = "60,62,64",
        ),
        Session(
            startedAt = 1751586400000L,
            plannedMillis = 3_600_000L,
            totalMillis = 3_650_000L,
            calmness = 0,
            people = emptyList(),
            notes = "",
            type = "Vipassana",
        ),
    )

    // ---------- envelope backup (existing behavior, pinned for the refactor) ----------

    @Test
    fun `envelope backup round-trips sessions and people`() {
        val json = exportJson(sits, listOf(Person(name = "ma")), "0.3.4", 1751600000000L)
        val bundle = parseBackup(json)
        assertEquals(sits.map { it.copy(id = 0) }, bundle.sessions)
        assertEquals(listOf("ma"), bundle.people.map { it.name })
    }

    // ---------- sit lines (jsonl sync format) ----------

    @Test
    fun `exportSitLines emits one json object per line`() {
        val text = exportSitLines(sits)
        val lines = text.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
    }

    @Test
    fun `sit lines round-trip all fields`() {
        val parsed = parseSitLines(exportSitLines(sits))
        assertEquals(sits.map { it.copy(id = 0) }, parsed)
    }

    @Test
    fun `parseSitLines skips corrupt and blank lines`() {
        val good = exportSitLines(sits).trim().lines()
        val text = listOf(good[0], "", "not json at all", "{\"truncated\":", good[1]).joinToString("\n")
        val parsed = parseSitLines(text)
        assertEquals(sits.map { it.copy(id = 0) }, parsed)
    }

    @Test
    fun `parseSitLines applies defaults for missing fields`() {
        val parsed = parseSitLines("""{"startedAt":123,"plannedMillis":60000,"totalMillis":60000}""")
        assertEquals(1, parsed.size)
        val s = parsed.single()
        assertEquals(123L, s.startedAt)
        assertEquals(0, s.calmness)
        assertEquals(emptyList<String>(), s.people)
        assertEquals("", s.notes)
        assertEquals("Meditation", s.type)
        assertEquals("", s.hrSeries)
    }

    // ---------- parseHistoryFile (import sniffing: envelope or jsonl) ----------

    @Test
    fun `parseHistoryFile reads an envelope backup`() {
        val bundle = parseHistoryFile(exportJson(sits, listOf(Person(name = "ma")), "0.3.4", 0L))
        assertEquals(sits.map { it.copy(id = 0) }, bundle.sessions)
        assertEquals(listOf("ma"), bundle.people.map { it.name })
    }

    @Test
    fun `parseHistoryFile reads sit lines with no people`() {
        val bundle = parseHistoryFile(exportSitLines(sits))
        assertEquals(sits.map { it.copy(id = 0) }, bundle.sessions)
        assertTrue(bundle.people.isEmpty())
    }

    @Test(expected = BackupFormatException::class)
    fun `parseHistoryFile rejects garbage`() {
        parseHistoryFile("definitely not\na history file")
    }

    @Test
    fun `parseSitLines on an envelope backup returns nothing`() {
        // A whole backup file is one giant JSON object on (potentially) many lines —
        // it must not be mistaken for sit lines.
        val envelope = exportJson(sits, emptyList(), "0.3.4", 0L)
        assertTrue(parseSitLines(envelope).isEmpty())
    }

    // ---------- device provenance (multi-device history) ----------

    @Test
    fun `sit lines never contain a device field`() {
        // Schema-1 purity: in the shared folder the FILENAME carries device identity;
        // records must stay interchangeable across devices.
        val line = exportSitLines(listOf(sits[0].copy(device = "mac-m3-max-16a484")))
        assertTrue(!line.contains("\"device\""))
    }

    @Test
    fun `deviceIdFromFileName strips the jsonl extension`() {
        assertEquals("mac-m3-max-16a484", deviceIdFromFileName("mac-m3-max-16a484.jsonl"))
        assertEquals("pixel-9-7cd72c", deviceIdFromFileName(" pixel-9-7cd72c.jsonl "))
        assertEquals("plain-name", deviceIdFromFileName("plain-name"))
    }
}
