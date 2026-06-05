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
import nishparadox.mastishka.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeditationViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settings = SettingsStore(app)

    val people = db.personDao().observeAll()
    val sessions = db.sessionDao().observeAll()
    val timerState = TimerService.state

    // Editable, persisted settings mirrored into Compose state.
    var durationMinutes by mutableIntStateOf(60)
        private set
    var gongVolume by mutableFloatStateOf(0.7f)
        private set
    var gongType by mutableStateOf(GongType.MEDIUM)
        private set
    var darkTheme by mutableStateOf(true)
        private set

    // People selected for the current sit's metta (reset after saving).
    val selectedPeople = mutableStateListOf<String>()

    private var testPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch { durationMinutes = settings.durationMinutes.first() }
        viewModelScope.launch { gongVolume = settings.gongVolume.first() }
        viewModelScope.launch { gongType = settings.gongType.first() }
        viewModelScope.launch { darkTheme = settings.darkTheme.first() }
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
        val session = Session(
            startedAt = s.startedAtEpoch,
            plannedMillis = s.plannedMillis,
            totalMillis = s.elapsedMillis,
            calmness = calmness,
            people = selectedPeople.toList(),
            notes = notes.trim(),
        )
        viewModelScope.launch {
            db.sessionDao().insert(session)
            selectedPeople.clear()
            TimerService.reset()
            onDone()
        }
    }

    fun discardSit() {
        selectedPeople.clear()
        TimerService.reset()
    }

    /** Play the gong once at the current volume so the user can set the level before sitting. */
    fun testGong() {
        stopTestGong()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
