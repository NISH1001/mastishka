package nishparadox.mastishka.data

import kotlin.math.pow

/**
 * Pure logic for guided sits — no Android dependencies, so it is unit-testable.
 *
 * The app ships no audio. Built-in recordings are a catalog of names and official public
 * links; each downloads once, on demand, straight from its publisher to the phone. Anything
 * else (for example recordings that need a login) is added by the user from their own files.
 */

/** A recording the app knows how to fetch from its publisher's public link. */
data class CatalogEntry(
    val id: String,           // stable; also names the downloaded file. Never starts with "user-".
    val title: String,
    val url: String,
    val durationMillis: Long, // approximate, shown before download
    val sizeBytes: Long,      // shown before download; fallback total for progress
)

/**
 * Built-in recordings. To add a teacher or recording, add a line here — the link must be one
 * the publisher serves publicly, and the audio itself must never be committed to the repo.
 */
val GUIDE_CATALOG: List<CatalogEntry> = listOf(
    CatalogEntry(
        id = "goenka-mini-anapana",
        title = "Goenka · Mini Anapana",
        url = "https://discourses.dhamma.org/oml/recordings/uuid/d6c3b1c1-2c6a-4db8-8de0-0c6ada1e2030.mp3",
        durationMillis = 10 * 60_000L,
        sizeBytes = 9_713_121L,
    ),
    CatalogEntry(
        id = "goenka-mini-anapana-intro",
        title = "Goenka · Mini Anapana with introduction",
        url = "https://discourses.dhamma.org/oml/recordings/uuid/4603a502-8fc7-44a3-9b18-f9d06941dc4b.mp3",
        durationMillis = 25 * 60_000L + 40_000L,
        sizeBytes = 24_772_212L,
    ),
)

/**
 * Where old students can get the 1-hour group sittings, opened in the browser. Points at the
 * login form (which then forwards to the English group sittings) rather than the listing itself,
 * because the listing greets a logged-out visitor with a bare "not authorized" message.
 */
const val GROUP_SITTINGS_URL =
    "https://discourses.dhamma.org/login?redirect=%2Frecordings%2Fgroup_sittings%3Flanguages%255Biso_code%255D%3Den"

/** One row of the Guided list: a catalog recording (downloaded or not) or a user-added file. */
data class Recording(
    val id: String,           // catalog id, or the stored file name for a user-added file
    val title: String,
    val durationMillis: Long, // 0 = unknown
    val sizeBytes: Long,
    val downloaded: Boolean,  // on the phone and playable
    val builtIn: Boolean,
)

private const val USER_PREFIX = "user-"

/** Display title for a recording: its file name without the extension. */
fun recordingTitle(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0) fileName.substring(0, dot) else fileName
}

/**
 * The practice a recording is for, read from its title, or null when the title doesn't say.
 * Choosing a recording pre-selects this practice chip (the user can still change it).
 */
fun practiceFor(title: String): String? {
    val t = title.lowercase()
    return when {
        "anapana" in t -> "Anapana"
        "vipassana" in t || "group sitting" in t -> "Vipassana"
        else -> null
    }
}

/** File name under the app's guided folder that holds the recording with this id. */
fun recordingFileName(id: String): String = if (id.startsWith(USER_PREFIX)) id else "$id.mp3"

/** Stored file name (and id) for a file the user adds, from the name their picker reported. */
fun userRecordingFileName(displayName: String?): String {
    val safe = (displayName ?: "")
        .replace(Regex("""[/\\:*?"<>|\p{Cntrl}]"""), "_")
        .trim()
        .trim('.')
        .takeLast(100)
    return USER_PREFIX + safe.ifBlank { "recording" }
}

/**
 * The Guided list: catalog recordings first, in catalog order, then the user's own files by
 * title. [files] maps each file name present in the guided folder to its measured length
 * (0 = unknown); a catalog entry with no file is listed as not yet downloaded.
 */
fun buildLibrary(catalog: List<CatalogEntry>, files: Map<String, Long>): List<Recording> {
    val builtIn = catalog.map { e ->
        val measured = files[recordingFileName(e.id)]
        Recording(
            id = e.id,
            title = e.title,
            durationMillis = measured?.takeIf { it > 0 } ?: e.durationMillis,
            sizeBytes = e.sizeBytes,
            downloaded = measured != null,
            builtIn = true,
        )
    }
    val own = files.filterKeys { it.startsWith(USER_PREFIX) }.map { (name, duration) ->
        Recording(
            id = name,
            title = recordingTitle(name.removePrefix(USER_PREFIX)),
            durationMillis = duration,
            sizeBytes = 0L,
            downloaded = true,
            builtIn = false,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    return builtIn + own
}

/** Range of the volume slider in decibels: 0% is silent, just above it is -40 dB, 100% is 0 dB. */
private const val VOLUME_RANGE_DB = 40f

/**
 * Map the 0..1 volume slider to a player gain on a perceptual (logarithmic) curve. Loudness is
 * heard in decibels, so a linear multiplier made low settings still loud (10% was only -20 dB);
 * here each step of the slider is an even step in perceived loudness.
 */
fun volumeGain(slider: Float): Float {
    val s = slider.coerceIn(0f, 1f)
    if (s == 0f) return 0f
    return 10f.pow(-VOLUME_RANGE_DB * (1f - s) / 20f)
}

/**
 * Sit clock that counts only time spent running, so a paused guided sit does not accrue time.
 * Immutable; all times are caller-supplied monotonic millis.
 */
data class SitClock(private val accumulated: Long, private val resumedAt: Long?) {
    val paused: Boolean get() = resumedAt == null

    fun elapsed(now: Long): Long = accumulated + (resumedAt?.let { now - it } ?: 0L)

    fun pause(now: Long): SitClock = if (paused) this else SitClock(elapsed(now), null)

    fun resume(now: Long): SitClock = if (paused) SitClock(accumulated, now) else this

    companion object {
        fun startedAt(now: Long) = SitClock(0L, now)
    }
}
