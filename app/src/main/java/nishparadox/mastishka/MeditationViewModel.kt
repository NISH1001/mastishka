package nishparadox.mastishka

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nishparadox.mastishka.data.AppDatabase
import nishparadox.mastishka.data.GongType
import nishparadox.mastishka.data.Person
import nishparadox.mastishka.data.Session
import nishparadox.mastishka.data.SettingsStore
import nishparadox.mastishka.health.HealthConnectManager
import nishparadox.mastishka.service.TimerService
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeditationViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settings = SettingsStore(app)
    private val health = HealthConnectManager(app)

    val people = db.personDao().observeAll()
    val sessions = db.sessionDao().observeAll()
    val timerState = TimerService.state

    // Editable, persisted settings mirrored into Compose state.
    var durationMinutes by mutableIntStateOf(5)
        private set
    var gongVolume by mutableFloatStateOf(0.7f)
        private set
    var gongType by mutableStateOf(GongType.MEDIUM)
        private set
    var darkTheme by mutableStateOf(true)
        private set
    // Empty = no specific practice chosen (falls back to "Meditation" when saved).
    var meditationType by mutableStateOf("")
        private set
    // Capture heart rate for the sit (only meaningful when Health Connect is connected).
    var monitorHeartRate by mutableStateOf(false)
        private set
    // Title heading language: true = Nepali (मस्तिष्क), false = English (Mastishka).
    var nepaliTitle by mutableStateOf(true)
        private set

    // People selected for the current sit's metta (reset after saving).
    val selectedPeople = mutableStateListOf<String>()

    // Health Connect
    var hcStatus by mutableIntStateOf(HealthConnectClient.SDK_UNAVAILABLE)
        private set
    var hcConnected by mutableStateOf(false)
        private set
    val hcPermissions: Set<String> get() = health.permissions
    val hcAvailable: Boolean get() = hcStatus == HealthConnectClient.SDK_AVAILABLE
    val hcUpdateRequired: Boolean
        get() = hcStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    // Heart-rate result for the just-finished sit, shown on the post-sit screen.
    var lastSitHr by mutableStateOf<HrSummary?>(null)
        private set
    var hrLoading by mutableStateOf(false)
        private set

    private var testPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch { durationMinutes = settings.durationMinutes.first() }
        viewModelScope.launch { gongVolume = settings.gongVolume.first() }
        viewModelScope.launch { gongType = settings.gongType.first() }
        viewModelScope.launch { darkTheme = settings.darkTheme.first() }
        viewModelScope.launch { meditationType = settings.meditationType.first() }
        viewModelScope.launch { monitorHeartRate = settings.monitorHeartRate.first() }
        viewModelScope.launch { nepaliTitle = settings.nepaliTitle.first() }
        refreshHealthConnect()
    }

    fun toggleNepaliTitle() {
        nepaliTitle = !nepaliTitle
        viewModelScope.launch { settings.setNepaliTitle(nepaliTitle) }
    }

    fun updateMonitorHeartRate(enabled: Boolean) {
        monitorHeartRate = enabled
        viewModelScope.launch { settings.setMonitorHeartRate(enabled) }
    }

    /** Read heart rate for the just-finished sit (called from the post-sit screen). */
    fun loadHeartRateForCurrentSit() {
        if (!hcConnected || !monitorHeartRate) return
        val s = timerState.value
        if (s.elapsedMillis <= 0L) return
        hrLoading = true
        viewModelScope.launch {
            val bpms = health.readHeartRate(s.startedAtEpoch, s.startedAtEpoch + s.elapsedMillis)
            lastSitHr = summarize(bpms)
            hrLoading = false
        }
    }

    /** Tap to select; tap the selected one again to clear (back to generic). */
    fun updateMeditationType(type: String) {
        meditationType = if (meditationType == type) "" else type
        viewModelScope.launch { settings.setMeditationType(meditationType) }
    }

    fun refreshHealthConnect() {
        hcStatus = health.status()
        viewModelScope.launch { hcConnected = health.hasAllPermissions() }
    }

    fun onHealthPermissionResult(granted: Set<String>) {
        hcConnected = granted.containsAll(health.permissions)
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        viewModelScope.launch { settings.setDarkTheme(darkTheme) }
    }

    fun updateGongType(type: GongType) {
        gongType = type
        viewModelScope.launch { settings.setGongType(type) }
    }

    fun setDuration(minutes: Int) {
        durationMinutes = minutes.coerceIn(1, 12 * 60)
        viewModelScope.launch { settings.setDurationMinutes(durationMinutes) }
    }

    fun updateGongVolume(volume: Float) {
        gongVolume = volume.coerceIn(0f, 1f)
        viewModelScope.launch { settings.setGongVolume(gongVolume) }
    }

    fun startSit() {
        selectedPeople.clear()
        lastSitHr = null
        TimerService.start(getApplication(), durationMinutes * 60_000L, gongVolume, gongType.rawResId)
    }

    fun endSit() {
        TimerService.end(getApplication())
    }

    fun togglePerson(name: String) {
        if (selectedPeople.contains(name)) selectedPeople.remove(name) else selectedPeople.add(name)
    }

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { db.personDao().insert(Person(name = trimmed)) }
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch { db.personDao().delete(id) }
    }

    fun saveSession(calmness: Int, notes: String, onDone: () -> Unit) {
        val s = timerState.value
        val hr = lastSitHr
        val session = Session(
            startedAt = s.startedAtEpoch,
            plannedMillis = s.plannedMillis,
            totalMillis = s.elapsedMillis,
            calmness = calmness,
            people = selectedPeople.toList(),
            notes = notes.trim(),
            type = meditationType.ifBlank { "Meditation" },
            hrAvg = hr?.avg ?: 0,
            hrMin = hr?.min ?: 0,
            hrMax = hr?.max ?: 0,
            hrSeries = hr?.seriesCsv ?: "",
        )
        viewModelScope.launch {
            val id = db.sessionDao().insert(session)
            if (hcConnected) {
                health.writeMeditation(
                    startMillis = session.startedAt,
                    endMillis = session.startedAt + session.totalMillis,
                    title = session.type,
                    notes = session.notes,
                    clientRecordId = "mastishka-$id",
                )
                // If HR wasn't captured on the post-sit screen yet, try once more now.
                if (hr == null && monitorHeartRate) {
                    summarize(health.readHeartRate(session.startedAt, session.startedAt + session.totalMillis))
                        ?.let { db.sessionDao().updateHeartRate(id, it.avg, it.min, it.max, it.seriesCsv) }
                }
            }
            selectedPeople.clear()
            lastSitHr = null
            TimerService.reset()
            onDone()
        }
    }

    /** Push every saved sit to Health Connect. Dedup-safe via stable clientRecordId.
     *  Calls back with the number written, or -1 if not connected. */
    fun syncAllSessions(onResult: (Int) -> Unit) {
        if (!hcConnected) { onResult(-1); return }
        viewModelScope.launch {
            val all = sessions.first()
            var count = 0
            for (s in all) {
                val ok = health.writeMeditation(
                    startMillis = s.startedAt,
                    endMillis = s.startedAt + s.totalMillis,
                    title = s.type,
                    notes = s.notes,
                    clientRecordId = "mastishka-${s.id}",
                )
                if (ok) count++
                // Also backfill heart rate for each sit from Health Connect.
                summarize(health.readHeartRate(s.startedAt, s.startedAt + s.totalMillis))
                    ?.let { db.sessionDao().updateHeartRate(s.id, it.avg, it.min, it.max, it.seriesCsv) }
            }
            onResult(count)
        }
    }

    /** Pull heart rate from Health Connect for any sit still missing it (auto on History open).
     *  No-op unless Health Connect is connected and "Monitor heart rate" is on. */
    fun backfillHeartRate(onDone: (Int) -> Unit = {}) {
        if (!hcConnected || !monitorHeartRate) { onDone(0); return }
        viewModelScope.launch {
            val all = sessions.first()
            var filled = 0
            for (s in all) {
                if (s.hrAvg > 0) continue
                summarize(health.readHeartRate(s.startedAt, s.startedAt + s.totalMillis))?.let {
                    db.sessionDao().updateHeartRate(s.id, it.avg, it.min, it.max, it.seriesCsv)
                    filled++
                }
            }
            onDone(filled)
        }
    }

    fun deleteSessions(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { db.sessionDao().deleteByIds(ids) }
    }

    fun discardSit() {
        selectedPeople.clear()
        lastSitHr = null
        TimerService.reset()
    }

    /** Reduce raw bpm samples to avg/min/max + a ~60-point series for charting. */
    private fun summarize(bpms: List<Long>): HrSummary? {
        if (bpms.isEmpty()) return null
        val ints = bpms.map { it.toInt() }
        val n = 60
        val series = if (ints.size <= n) ints else (0 until n).map { i ->
            val from = i * ints.size / n
            val to = ((i + 1) * ints.size / n).coerceAtMost(ints.size)
            if (to > from) ints.subList(from, to).average().toInt() else ints[from.coerceIn(0, ints.size - 1)]
        }
        return HrSummary(avg = ints.average().toInt(), min = ints.min(), max = ints.max(), series = series)
    }

    /** Play the gong once at the current volume so the user can set the level before sitting. */
    fun testGong() {
        stopTestGong()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    // Media channel — see TimerService.playGong() for the rationale.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }
        val afd = getApplication<Application>().resources.openRawResourceFd(gongType.rawResId) ?: return
        afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        player.setVolume(gongVolume, gongVolume)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { stopTestGong() }
        player.prepareAsync()
        testPlayer = player
    }

    fun stopTestGong() {
        testPlayer?.let { runCatching { it.release() } }
        testPlayer = null
    }

    override fun onCleared() {
        stopTestGong()
        super.onCleared()
    }
}

/** Heart-rate summary for a sit: avg/min/max bpm + a downsampled series for charting. */
data class HrSummary(val avg: Int, val min: Int, val max: Int, val series: List<Int>) {
    val seriesCsv: String get() = series.joinToString(",")
}
