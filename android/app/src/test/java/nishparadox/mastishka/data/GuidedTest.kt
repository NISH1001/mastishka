package nishparadox.mastishka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTest {

    // ---------- library listing ----------

    private val catalog = listOf(
        CatalogEntry("a-short", "Teacher · Short", "https://example.org/a.mp3", 600_000, 9_000_000),
        CatalogEntry("b-long", "Teacher · Long", "https://example.org/b.mp3", 1_500_000, 24_000_000),
    )

    @Test
    fun `title is the file name without its extension`() {
        assertEquals("Goenka - Mini Anapana", recordingTitle("Goenka - Mini Anapana.mp3"))
        assertEquals("sitting.v2", recordingTitle("sitting.v2.ogg"))
        assertEquals("noextension", recordingTitle("noextension"))
        assertEquals(".hidden", recordingTitle(".hidden"))
    }

    @Test
    fun `nothing downloaded lists the catalog as not yet on the phone`() {
        val lib = buildLibrary(catalog, emptyMap())
        assertEquals(listOf("a-short", "b-long"), lib.map { it.id })
        assertTrue(lib.none { it.downloaded })
        assertTrue(lib.all { it.builtIn })
        assertEquals(600_000L, lib[0].durationMillis) // catalog estimate until measured
        assertEquals(9_000_000L, lib[0].sizeBytes)
    }

    @Test
    fun `a downloaded catalog recording is playable and uses its measured length`() {
        val lib = buildLibrary(catalog, mapOf("b-long.mp3" to 1_548_000L, "a-short.mp3" to 0L))
        assertTrue(lib.all { it.downloaded })
        assertEquals(1_548_000L, lib.first { it.id == "b-long" }.durationMillis)
        assertEquals(600_000L, lib.first { it.id == "a-short" }.durationMillis) // unknown → estimate
    }

    @Test
    fun `user files follow the catalog sorted by title and stray files are ignored`() {
        val lib = buildLibrary(
            catalog,
            mapOf(
                "user-zen bell.ogg" to 60_000L,
                "user-Group sitting.mp3" to 3_700_000L,
                "a-short.part" to 0L,
                "notes.txt" to 0L,
            ),
        )
        assertEquals(listOf("a-short", "b-long", "user-Group sitting.mp3", "user-zen bell.ogg"), lib.map { it.id })
        assertEquals(listOf("Group sitting", "zen bell"), lib.filter { !it.builtIn }.map { it.title })
        assertTrue(lib.filter { !it.builtIn }.all { it.downloaded })
        assertFalse(lib[0].downloaded) // a partial download is not a recording
    }

    @Test
    fun `stored file names keep the id rules and survive awkward names`() {
        assertEquals("a-short.mp3", recordingFileName("a-short"))
        assertEquals("user-x.ogg", recordingFileName("user-x.ogg"))
        assertEquals("user-Group sitting.mp3", userRecordingFileName("Group sitting.mp3"))
        assertEquals("user-a_b_c.mp3", userRecordingFileName("a/b\\c.mp3"))
        assertEquals("user-recording", userRecordingFileName(null))
        assertEquals("user-recording", userRecordingFileName("  "))
        assertFalse(userRecordingFileName("../../etc/passwd").contains("/"))
    }

    @Test
    fun `built-in catalog ids are unique, use https and never look like user files`() {
        assertEquals(GUIDE_CATALOG.size, GUIDE_CATALOG.map { it.id }.toSet().size)
        assertTrue(GUIDE_CATALOG.all { it.url.startsWith("https://") })
        assertTrue(GUIDE_CATALOG.none { it.id.startsWith("user-") })
    }

    @Test
    fun `a recording's title says which practice it is for`() {
        assertEquals("Anapana", practiceFor("Goenka · Mini Anapana"))
        assertEquals("Anapana", practiceFor("ANAPANA with introduction"))
        assertEquals("Vipassana", practiceFor("Goenka - Vipassana 1 hour"))
        assertEquals("Vipassana", practiceFor("English Group Sitting"))
        assertEquals(null, practiceFor("Zen bell"))
        assertTrue(GUIDE_CATALOG.all { practiceFor(it.title) != null })
    }

    // ---------- in-app dhamma.org browser ----------

    @Test
    fun `only the publisher's own hosts count as dhamma hosts`() {
        assertTrue(isDhammaHost("discourses.dhamma.org"))
        assertTrue(isDhammaHost("www.dhamma.org"))
        assertTrue(isDhammaHost("DHAMMA.org"))
        assertFalse(isDhammaHost("dhamma.org.evil.com"))
        assertFalse(isDhammaHost("notdhamma.org"))
        assertFalse(isDhammaHost(null))
    }

    @Test
    fun `audio urls are recognised by path, ignoring the query`() {
        assertTrue(isAudioUrl("https://discourses.dhamma.org/oml/recordings/uuid/abc.mp3"))
        assertTrue(isAudioUrl("https://x.dhamma.org/a/b.MP3?token=1"))
        assertFalse(isAudioUrl("https://discourses.dhamma.org/recordings/group_sittings?x=.mp3"))
        assertFalse(isAudioUrl("https://discourses.dhamma.org/login"))
        assertFalse(isAudioUrl(null))
    }

    @Test
    fun `site labels are tidied into short titles without repeats`() {
        // exactly what the site shows for an "Edited for VGS" sitting
        assertEquals(
            "Vipassana · Long Instructions (Edited for VGS) · Dhamma Setu · 2000",
            tidyRecordingLabel(
                "Group Sitting • Group Sitting • Long Instructions (Edited for VGS) • Group Sitting • " +
                    "Long Instructions (Edited for VGS) • English • English • Dhamma Setu • 2000"
            ),
        )
        // a recording page heading
        assertEquals(
            "Vipassana · Short Instructions · Juhu, Mumbai, India · 1987",
            tidyRecordingLabel("Group Sitting • Short Instructions • English • Juhu, Mumbai, India • 1987"),
        )
        // a list card, flagged as a group sitting by the page
        assertEquals(
            "Vipassana · Short Instructions · Juhu, Mumbai, India · 1987",
            tidyRecordingLabel("Short Instructions · Juhu, Mumbai, India · 1987", groupSitting = true),
        )
        // other languages are kept; non-sittings get no practice prefix
        assertEquals("Vipassana · Long Instructions · Hindi · Dhamma Giri", tidyRecordingLabel("Group Sitting • Long Instructions • Hindi • Dhamma Giri"))
        assertEquals("Day 1 · 10-Day Discourse", tidyRecordingLabel("Day 1 • 10-Day Discourse • English"))
        assertEquals("", tidyRecordingLabel("  "))
    }

    @Test
    fun `a saved web recording is a user file named after its title, never cut from the front`() {
        val title = tidyRecordingLabel(
            "Group Sitting • Group Sitting • Long Instructions (Edited for VGS) • Group Sitting • " +
                "Long Instructions (Edited for VGS) • English • English • Dhamma Setu • 2000"
        )
        val name = webRecordingFileName(title, "https://resources.dhamma.org/oml/recordings/uuid/abc.mp3")
        assertEquals("user-Vipassana · Long Instructions (Edited for VGS) · Dhamma Setu · 2000.mp3", name)
        assertEquals("Vipassana", practiceFor(recordingTitle(name)))
        // no title: fall back to the file's own name
        assertEquals("user-abc.mp3", webRecordingFileName("  ", "https://x.dhamma.org/r/abc.mp3?t=1"))
    }

    // ---------- volume curve ----------

    @Test
    fun `volume curve is silent at zero and full at one`() {
        assertEquals(0f, volumeGain(0f), 0f)
        assertEquals(1f, volumeGain(1f), 1e-6f)
    }

    @Test
    fun `low slider settings are far quieter than a linear scale`() {
        // 10% used to mean 0.1 amplitude (only -20 dB); it must now be much softer.
        assertTrue(volumeGain(0.1f) < 0.03f)
        assertTrue(volumeGain(0.1f) > 0f)
        assertTrue(volumeGain(0.5f) < 0.2f)
    }

    @Test
    fun `volume curve rises steadily and clamps out-of-range input`() {
        var prev = -1f
        for (i in 0..20) {
            val g = volumeGain(i / 20f)
            assertTrue("gain must increase at step $i", g > prev)
            prev = g
        }
        assertEquals(0f, volumeGain(-1f), 0f)
        assertEquals(1f, volumeGain(5f), 1e-6f)
    }

    // ---------- pause-aware clock ----------

    @Test
    fun `clock counts only time spent running`() {
        var c = SitClock.startedAt(1_000)
        assertEquals(500, c.elapsed(1_500))
        c = c.pause(2_000)                // ran 1000
        assertTrue(c.paused)
        assertEquals(1_000, c.elapsed(9_000)) // frozen while paused
        c = c.resume(10_000)
        assertFalse(c.paused)
        assertEquals(1_250, c.elapsed(10_250))
    }

    @Test
    fun `repeated pause or resume is harmless`() {
        var c = SitClock.startedAt(0).pause(100).pause(500)
        assertEquals(100, c.elapsed(900))
        c = c.resume(1_000).resume(1_400)
        assertEquals(600, c.elapsed(1_500))
    }
}
