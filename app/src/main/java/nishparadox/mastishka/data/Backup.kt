package nishparadox.mastishka.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Round-trippable JSON backup of all on-device history (sits + people). Pure functions over
 * [org.json] — no Android dependencies, so they're trivially unit-testable. Health Connect is
 * NOT a backup (it never holds calmness or metta people, and the app never reads sits back),
 * so this is the only way to move history across a reinstall or a new phone.
 */

/** Current backup envelope version. Bump only on a breaking format change. */
const val BACKUP_SCHEMA = 1

/** Parsed contents of a backup file, ready to merge into the database. */
data class BackupBundle(val sessions: List<Session>, val people: List<Person>)

/** Raised when a file isn't a Mastishka backup we can read (malformed, or a newer schema). */
class BackupFormatException(message: String) : Exception(message)

/** Serialize all sits and people into a single pretty-printed backup document. */
fun exportJson(
    sessions: List<Session>,
    people: List<Person>,
    versionName: String,
    exportedAt: Long,
): String {
    val sessionsJson = JSONArray()
    for (s in sessions) {
        sessionsJson.put(
            JSONObject().apply {
                put("startedAt", s.startedAt)
                put("plannedMillis", s.plannedMillis)
                put("totalMillis", s.totalMillis)
                put("calmness", s.calmness)
                put("people", JSONArray(s.people)) // real array, not the internal "||" string
                put("notes", s.notes)
                put("type", s.type)
                put("hrAvg", s.hrAvg)
                put("hrMin", s.hrMin)
                put("hrMax", s.hrMax)
                put("hrSeries", s.hrSeries) // comma-string, as stored
            }
        )
    }
    val peopleJson = JSONArray(people.map { it.name })

    return JSONObject().apply {
        put("app", "mastishka")
        put("schema", BACKUP_SCHEMA)
        put("versionName", versionName)
        put("exportedAt", exportedAt)
        put("sessions", sessionsJson)
        put("people", peopleJson)
    }.toString(2)
}

/**
 * Parse a backup document. Tolerant of missing fields (falls back to the entity defaults so an
 * older/partial export still imports), but rejects malformed JSON or an unknown future schema.
 * Row ids are intentionally dropped — they're regenerated on insert.
 */
fun parseBackup(text: String): BackupBundle {
    val root = try {
        JSONObject(text)
    } catch (e: Exception) {
        throw BackupFormatException("Not a valid backup file.")
    }

    val schema = root.optInt("schema", 1)
    if (schema > BACKUP_SCHEMA) {
        throw BackupFormatException("This backup was made by a newer version of the app.")
    }
    if (!root.has("sessions") && !root.has("people")) {
        throw BackupFormatException("Not a Mastishka backup file.")
    }

    val sessions = mutableListOf<Session>()
    val sessionsJson = root.optJSONArray("sessions") ?: JSONArray()
    for (i in 0 until sessionsJson.length()) {
        val o = sessionsJson.optJSONObject(i) ?: continue
        val peopleArr = o.optJSONArray("people") ?: JSONArray()
        val peopleList = (0 until peopleArr.length()).mapNotNull { peopleArr.optString(it, null) }
        sessions.add(
            Session(
                startedAt = o.optLong("startedAt", 0L),
                plannedMillis = o.optLong("plannedMillis", 0L),
                totalMillis = o.optLong("totalMillis", 0L),
                calmness = o.optInt("calmness", 0),
                people = peopleList,
                notes = o.optString("notes", ""),
                type = o.optString("type", "Meditation"),
                hrAvg = o.optInt("hrAvg", 0),
                hrMin = o.optInt("hrMin", 0),
                hrMax = o.optInt("hrMax", 0),
                hrSeries = o.optString("hrSeries", ""),
            )
        )
    }

    val people = mutableListOf<Person>()
    val peopleJson = root.optJSONArray("people") ?: JSONArray()
    for (i in 0 until peopleJson.length()) {
        val name = peopleJson.optString(i, "").trim()
        if (name.isNotEmpty()) people.add(Person(name = name))
    }

    return BackupBundle(sessions = sessions, people = people)
}
