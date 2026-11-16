package nishparadox.mastishka

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nishparadox.mastishka.data.AppDatabase
import nishparadox.mastishka.data.BackupFormatException
import nishparadox.mastishka.data.GongType
import nishparadox.mastishka.data.Person
import nishparadox.mastishka.data.Recording
import nishparadox.mastishka.data.Session
import nishparadox.mastishka.data.SettingsStore
import nishparadox.mastishka.data.deviceIdFromFileName
import nishparadox.mastishka.data.exportJson
import nishparadox.mastishka.data.exportSitLines
import nishparadox.mastishka.data.parseHistoryFile
import nishparadox.mastishka.data.GUIDE_CATALOG
import nishparadox.mastishka.data.buildLibrary
import nishparadox.mastishka.data.parseSitLines
import nishparadox.mastishka.data.practiceFor
import nishparadox.mastishka.data.recordingFileName
import nishparadox.mastishka.data.userRecordingFileName
import nishparadox.mastishka.data.recordingTitle
import nishparadox.mastishka.data.volumeGain
import nishparadox.mastishka.data.webRecordingFileName
import nishparadox.mastishka.health.HealthConnectManager
import nishparadox.mastishka.service.TimerService
import nishparadox.mastishka.service.gongAudioAttributes
import nishparadox.mastishka.service.headphonesConnected
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
    // Drive sync: tree URI of the one granted folder (Drive/nishparadox/mastishka).
    // Everything inside is automatic — own file written, sibling devices discovered.
    var driveRoot by mutableStateOf("")
        private set
    val syncEnabled: Boolean get() = driveRoot.isNotEmpty()
    // Stable device id naming this device's sit file (pixel-9-1a2b3c); loaded in init.
    var deviceId by mutableStateOf("")
        private set
    val syncFileName: String get() = "$deviceId.jsonl"

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

    // Guided sits: the Silent/Guided switch and the list of recordings (built-in catalog,
    // downloaded on demand, plus files the user added).
    var guidedMode by mutableStateOf(false)
        private set
    var recordings by mutableStateOf(buildLibrary(GUIDE_CATALOG, emptyMap()))
        private set
    var selectedRecordingId by mutableStateOf("")
        private set
    /** Recording id → 0..1 while it is downloading (absent = not downloading). */
    val downloadProgress = mutableStateMapOf<String, Float>()
    /** One-shot message about a failed download or add, for the screen to show and clear. */
    var guideMessage by mutableStateOf<String?>(null)
        private set
    val selectedRecording: Recording? get() = recordings.firstOrNull { it.id == selectedRecordingId }
    /** Begin is possible: always for a silent sit; for a guided one only once the chosen
     *  recording is on the phone. */
    val canBegin: Boolean get() = !guidedMode || selectedRecording?.downloaded == true

    private var testPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch { durationMinutes = settings.durationMinutes.first() }
        viewModelScope.launch { gongVolume = settings.gongVolume.first() }
        viewModelScope.launch { gongType = settings.gongType.first() }
        viewModelScope.launch { darkTheme = settings.darkTheme.first() }
        viewModelScope.launch { meditationType = settings.meditationType.first() }
        viewModelScope.launch { monitorHeartRate = settings.monitorHeartRate.first() }
        viewModelScope.launch { nepaliTitle = settings.nepaliTitle.first() }
        viewModelScope.launch { driveRoot = settings.driveRoot.first() }
        viewModelScope.launch { deviceId = settings.deviceId() }
        viewModelScope.launch { guidedMode = settings.guidedMode.first() }
        viewModelScope.launch { selectedRecordingId = settings.guideSelected.first() }
        refreshRecordings()
        refreshHealthConnect()
    }

    /**
     * The one manual act, ever: the user grants the `Drive/nishparadox/mastishka` folder.
     * From then on the app finds/creates `sits/`, writes its own device file, and pulls every
     * other device's sits automatically — devices that don't exist yet included.
     */
    fun setupDriveFolder(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            settings.setDriveRoot(uri.toString())
            driveRoot = uri.toString()
            settings.setSyncUri("") // retire the old single-file grant, superseded by the folder
            val wrote = writeSitLines()
            val (added, devices) = pullSiblingSits()
            onResult(buildString {
                append(
                    if (wrote) "Drive sync on — this phone writes sits/$syncFileName"
                    else "Folder connected, but couldn't write the sit file"
                )
                if (added > 0) {
                    append(" · pulled $added sit${if (added == 1) "" else "s"} from ")
                    append("$devices device${if (devices == 1) "" else "s"}")
                }
            })
        }
    }

    /** Re-scan the folder for other devices' new sits (called on History open). */
    fun refreshDrive() {
        if (!syncEnabled) return
        viewModelScope.launch { pullSiblingSits() }
    }

    /** The granted folder's `sits/` subfolder, created on first use. IO — call off-main. */
    private fun sitsFolder(): DocumentFile? {
        if (driveRoot.isEmpty()) return null
        val root = DocumentFile.fromTreeUri(getApplication(), Uri.parse(driveRoot)) ?: return null
        return root.findFile("sits")?.takeIf { it.isDirectory } ?: root.createDirectory("sits")
    }

    /**
     * Merge new sits from every other device's jsonl in `sits/` into Room — and into Health
     * Connect (macOS has no HealthKit, so the phone is the ecosystem's health gateway).
     * Returns (sits added, devices they came from). Fails soft per file.
     */
    private suspend fun pullSiblingSits(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val folder = sitsFolder() ?: return@withContext 0 to 0
        val resolver = getApplication<Application>().contentResolver
        var added = 0
        val devices = mutableSetOf<String>()
        for (file in folder.listFiles()) {
            val name = file.name ?: continue
            if (!name.endsWith(".jsonl") || name == syncFileName) continue
            val device = deviceIdFromFileName(name)
            val text = runCatching {
                resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: continue
            val existing = db.sessionDao().allStartTimes().toSet()
            val fresh = parseSitLines(text)
                .filter { it.startedAt !in existing }
                .distinctBy { it.startedAt }
                .map { it.copy(device = device) }
            for (session in fresh) {
                val id = db.sessionDao().insert(session)
                added++
                devices += device
                if (hcConnected) {
                    health.writeMeditation(
                        startMillis = session.startedAt,
                        endMillis = session.startedAt + session.totalMillis,
                        title = session.type,
                        notes = session.notes,
                        clientRecordId = "mastishka-$id",
                    )
                }
            }
        }
        added to devices.size
    }

    private suspend fun documentDisplayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    fun disableSync() {
        viewModelScope.launch {
            settings.setDriveRoot("")
            driveRoot = ""
        }
    }

    /**
     * Rewrite this device's `sits/<device>.jsonl` inside the granted folder with its full
     * history (single-writer, whole-file — specs/ecosystem-protocol.md §3), creating folder
     * and file on first use. Fails soft: sync must never break saving a sit.
     */
    private suspend fun writeSitLines(): Boolean {
        if (!syncEnabled) return false
        return runCatching {
            // Only THIS device's sits — the file is this phone's voice in the shared folder;
            // other devices' sits already live in their own files.
            val text = exportSitLines(sessions.first().filter { it.device.isEmpty() })
            withContext(Dispatchers.IO) {
                val folder = sitsFolder() ?: error("sits folder unavailable")
                val file = folder.findFile(syncFileName)?.takeIf { it.isFile }
                    ?: folder.createFile("application/octet-stream", syncFileName)
                    ?: error("couldn't create $syncFileName")
                val resolver = getApplication<Application>().contentResolver
                // "wt" truncates: shorter content (after a deletion) must not leave stale bytes.
                resolver.openOutputStream(file.uri, "wt")
                    ?.use { it.write(text.toByteArray()) }
                    ?: error("could not open sync file")
            }
        }.isSuccess
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
        // A preview that is playing follows the slider, so the level can be set by ear.
        val gain = volumeGain(gongVolume)
        testPlayer?.let { runCatching { it.setVolume(gain, gain) } }
    }

    fun startSit() {
        stopTestGong() // a preview must not play over the sit
        selectedPeople.clear()
        lastSitHr = null
        val rec = selectedRecording.takeIf { guidedMode && it?.downloaded == true }
        if (rec == null) {
            TimerService.start(getApplication(), durationMinutes * 60_000L, gongVolume, gongType.rawResId)
        } else {
            TimerService.start(
                getApplication(), rec.durationMillis, gongVolume, gongType.rawResId,
                guideUri = Uri.fromFile(File(guideDir(), recordingFileName(rec.id))).toString(),
                guideTitle = rec.title,
            )
        }
    }

    fun pauseSit() = TimerService.pause(getApplication())

    fun resumeSit() = TimerService.resume(getApplication())

    // ---------- Guided sits ----------

    fun updateGuidedMode(enabled: Boolean) {
        guidedMode = enabled
        viewModelScope.launch { settings.setGuidedMode(enabled) }
        if (enabled) {
            refreshRecordings()
            matchPracticeTo(selectedRecording)
        }
    }

    /** Pre-select the practice chip a recording is for (Anapana, Vipassana), if its title says. */
    private fun matchPracticeTo(recording: Recording?) {
        val practice = recording?.let { practiceFor(it.title) } ?: return
        if (practice == meditationType) return
        meditationType = practice
        viewModelScope.launch { settings.setMeditationType(practice) }
    }

    fun clearGuideMessage() {
        guideMessage = null
    }

    /** App-private folder holding downloaded and user-added recordings. */
    private fun guideDir(): File =
        File(getApplication<Application>().filesDir, "guided").apply { mkdirs() }

    /** Choose a recording. One that is not on the phone yet starts downloading right away. */
    fun selectRecording(id: String) {
        selectedRecordingId = id
        viewModelScope.launch { settings.setGuideSelected(id) }
        val recording = recordings.firstOrNull { it.id == id }
        matchPracticeTo(recording)
        if (recording?.downloaded == false) downloadRecording(id)
    }

    /** Re-read the guided folder: which recordings are on the phone, and how long each is. */
    fun refreshRecordings(thenSelect: String? = null) {
        viewModelScope.launch {
            recordings = withContext(Dispatchers.IO) {
                val files = guideDir().listFiles().orEmpty()
                    .filter { it.isFile && !it.name.endsWith(".part") }
                    .associate { it.name to audioDurationMillis(it) }
                buildLibrary(GUIDE_CATALOG, files)
            }
            // A file the user just added: now that it is listed, match its practice too.
            thenSelect?.let { id -> matchPracticeTo(recordings.firstOrNull { it.id == id }) }
        }
    }

    private fun audioDurationMillis(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Download a catalog recording once, straight from its publisher's public link, into the
     * guided folder. Written to a `.part` file and renamed only when complete, so an
     * interrupted download never looks like a playable recording.
     */
    private fun downloadRecording(id: String) {
        val entry = GUIDE_CATALOG.firstOrNull { it.id == id } ?: return
        if (downloadProgress.containsKey(id)) return
        downloadProgress[id] = 0f
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    fetchAudio(
                        url = entry.url,
                        headers = emptyMap(),
                        targetName = recordingFileName(id),
                        fallbackTotal = entry.sizeBytes,
                        checkPlayable = false,
                    ) { downloadProgress[id] = it }
                }
            }
            downloadProgress.remove(id)
            if (result.isFailure) {
                guideMessage = "Couldn't download “${entry.title}”. Check your connection and tap it again."
            }
            refreshRecordings()
        }
    }

    /**
     * Fetch an audio file into the guided folder as [targetName], reporting progress 0..1.
     * Written to a `.part` file and renamed only when complete. With [checkPlayable], the
     * result must be readable as audio — a login or error page saved under an audio name is
     * rejected. Runs on the caller's (IO) dispatcher; throws on any failure.
     */
    private suspend fun fetchAudio(
        url: String,
        headers: Map<String, String>,
        targetName: String,
        fallbackTotal: Long,
        checkPlayable: Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val part = File(guideDir(), "$targetName.part")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: fallbackTotal.coerceAtLeast(1)
                var done = 0L
                var shown = 0
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != shown) {
                                shown = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
                if (done == 0L) error("empty download")
                if (checkPlayable && audioDurationMillis(part) <= 0L) error("not playable audio")
                if (!part.renameTo(File(guideDir(), targetName))) error("couldn't save")
            } finally {
                conn.disconnect()
            }
        } finally {
            part.delete()
        }
    }

    // ---------- In-app dhamma.org browser ----------

    /** State of a save started from the in-app browser. */
    sealed interface WebSave {
        data class Saving(val title: String, val progress: Float) : WebSave
        data class Saved(val title: String) : WebSave
        data class Failed(val title: String) : WebSave
    }

    var webSave by mutableStateOf<WebSave?>(null)
        private set

    fun clearWebSave() {
        if (webSave !is WebSave.Saving) webSave = null
    }

    /**
     * Save a recording the user opened in the in-app browser, using their own signed-in
     * session ([cookie]) — the same request their browser would make. It lands in the Guided
     * list as one of their own recordings and becomes the selected one.
     */
    fun saveWebRecording(url: String, title: String, cookie: String?, userAgent: String?) {
        if (webSave is WebSave.Saving) return
        val name = webRecordingFileName(title, url)
        val shown = recordingTitle(name.removePrefix("user-"))
        webSave = WebSave.Saving(shown, 0f)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val headers = buildMap {
                        if (!cookie.isNullOrBlank()) put("Cookie", cookie)
                        if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
                    }
                    fetchAudio(url, headers, name, fallbackTotal = 60L * 1024 * 1024, checkPlayable = true) {
                        webSave = WebSave.Saving(shown, it)
                    }
                }
            }
            webSave = if (result.isSuccess) WebSave.Saved(shown) else WebSave.Failed(shown)
            if (result.isSuccess) selectRecordingAfterRefresh(name)
        }
    }

    /** Copy an audio file the user picked into the guided folder and select it. */
    fun addRecording(uri: Uri) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val name = userRecordingFileName(documentDisplayName(uri))
                    val target = File(guideDir(), name)
                    val part = File(guideDir(), "$name.part")
                    try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            part.outputStream().use { input.copyTo(it) }
                        } ?: error("couldn't open file")
                        // Only keep it if the phone can actually read it as audio.
                        if (audioDurationMillis(part) <= 0L) error("not playable audio")
                        if (!part.renameTo(target)) error("couldn't save")
                    } finally {
                        part.delete()
                    }
                    name
                }
            }
            added.onSuccess { selectRecordingAfterRefresh(it) }
                .onFailure { guideMessage = "Couldn't add that file — it doesn't look like playable audio." }
        }
    }

    private fun selectRecordingAfterRefresh(id: String) {
        selectedRecordingId = id
        viewModelScope.launch { settings.setGuideSelected(id) }
        refreshRecordings(thenSelect = id)
    }

    /** Delete a recording from the phone. A built-in one goes back to "tap to download". */
    fun removeRecording(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { File(guideDir(), recordingFileName(id)).delete() }
            refreshRecordings()
        }
    }

    /**
     * Why a guided recording would be inaudible right now, or null if it should be heard.
     * The recording plays as media, and the app never changes the phone's media volume.
     */
    fun guidedSilenceWarning(): String? {
        val app = getApplication<Application>()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (gongVolume <= 0f) return "The volume slider below is at 0%, so the recording will be silent."
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || am.isStreamMute(AudioManager.STREAM_MUSIC)) {
            return "Your phone's media volume is at zero, so the recording will be silent. " +
                "Turn it up with the volume buttons."
        }
        // Best effort: "total silence" Do Not Disturb mutes media. Other modes usually allow it.
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
            return "Do Not Disturb is set to total silence, which mutes media, so the recording will be silent."
        }
        return null
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
            guide = s.guideTitle,
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
            writeSitLines()
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
            writeSitLines()
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
            // Late-arriving heart data must reach the Drive sit file too.
            if (filled > 0) writeSitLines()
            onDone(filled)
        }
    }

    fun deleteSessions(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            db.sessionDao().deleteByIds(ids)
            writeSitLines()
        }
    }

    /** Write every sit and person to [uri] as a JSON backup. Reports success on the main thread. */
    fun exportData(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = exportJson(
                    sessions = sessions.first(),
                    people = people.first(),
                    versionName = BuildConfig.VERSION_NAME,
                    exportedAt = System.currentTimeMillis(),
                )
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Could not open file for writing")
                }
            }.isSuccess
            onResult(ok)
        }
    }

    /** Restore history from [uri] — a backup envelope or another device's sit-lines jsonl —
     *  merging into existing history. Dedupes sits by start time and people by name, so
     *  re-importing the same file is a no-op. */
    fun importData(uri: Uri, onResult: (ImportSummary) -> Unit) {
        viewModelScope.launch {
            val summary = runCatching {
                val text = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not open file for reading")
                }
                val bundle = parseHistoryFile(text)

                // A foreign device's jsonl picked via plain import still gets provenance
                // (from its filename); envelopes and this device's own file stay local ("").
                val name = documentDisplayName(uri) ?: ""
                val sourceDevice = deviceIdFromFileName(name)
                    .takeIf { name.endsWith(".jsonl") && it != deviceId } ?: ""

                val existingStarts = db.sessionDao().allStartTimes().toSet()
                val newSessions = bundle.sessions
                    .filter { it.startedAt !in existingStarts }
                    .distinctBy { it.startedAt }
                    .map { if (sourceDevice.isEmpty()) it else it.copy(device = sourceDevice) }
                db.sessionDao().insertAll(newSessions)

                val existingNames = people.first().map { it.name.lowercase() }.toMutableSet()
                var peopleAdded = 0
                for (p in bundle.people) {
                    if (existingNames.add(p.name.lowercase())) {
                        db.personDao().insert(p)
                        peopleAdded++
                    }
                }

                if (newSessions.isNotEmpty()) writeSitLines()

                ImportSummary(
                    sessionsAdded = newSessions.size,
                    sessionsSkipped = bundle.sessions.size - newSessions.size,
                    peopleAdded = peopleAdded,
                )
            }.getOrElse { e ->
                val msg = (e as? BackupFormatException)?.message ?: "Couldn't read that backup file."
                ImportSummary(error = msg)
            }
            onResult(summary)
        }
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
        // Mirror TimerService.playGong(): media usage on headphones (so the test reaches the ears),
        // otherwise the alarm stream (and max its volume only while playing).
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val onHeadphones = headphonesConnected(am)
        val player = MediaPlayer().apply {
            setAudioAttributes(gongAudioAttributes(onHeadphones))
        }
        val afd = getApplication<Application>().resources.openRawResourceFd(gongType.rawResId) ?: return
        afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        val gain = volumeGain(gongVolume)
        player.setVolume(gain, gain)
        player.setOnPreparedListener { if (!onHeadphones) raiseAlarmVolume(); it.start() }
        player.setOnCompletionListener { stopTestGong() }
        player.prepareAsync()
        testPlayer = player
    }

    fun stopTestGong() {
        previewStop?.cancel()
        previewStop = null
        testPlayer?.let { runCatching { it.release() } }
        testPlayer = null
        restoreAlarmVolume()
    }

    private var previewStop: Job? = null

    /** True when the test button should preview the chosen recording rather than the gong. */
    val canPreviewRecording: Boolean get() = guidedMode && selectedRecording?.downloaded == true

    /**
     * Play the first seconds of the chosen recording exactly as a guided sit would — as media,
     * at the slider's level on top of the phone's media volume — so the level can be set
     * before sitting. Stops by itself.
     */
    fun testRecording() {
        stopTestGong()
        val rec = selectedRecording?.takeIf { it.downloaded } ?: return
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(File(guideDir(), recordingFileName(rec.id)).absolutePath)
            val gain = volumeGain(gongVolume)
            player.setVolume(gain, gain)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { stopTestGong() }
            player.setOnErrorListener { _, _, _ -> stopTestGong(); true }
            player.prepareAsync()
            testPlayer = player
            previewStop = viewModelScope.launch {
                delay(PREVIEW_MILLIS)
                previewStop = null
                stopTestGong()
            }
        } catch (e: Exception) {
            runCatching { player.release() }
            guideMessage = "Couldn't play that recording."
        }
    }

    // Max the alarm stream only while the test gong sounds, restoring the user's level after.
    // Mirrors TimerService so the test reflects the real sit. Alarm *volume* only — not schedules.
    private var savedAlarmVolume: Int? = null

    private fun raiseAlarmVolume() {
        val am = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (savedAlarmVolume == null) savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
    }

    private fun restoreAlarmVolume() {
        val saved = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching {
            (getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        }
    }

    override fun onCleared() {
        stopTestGong()
        super.onCleared()
    }
}

/** How long the test button previews a recording before stopping by itself. */
private const val PREVIEW_MILLIS = 20_000L

/** Heart-rate summary for a sit: avg/min/max bpm + a downsampled series for charting. */
data class HrSummary(val avg: Int, val min: Int, val max: Int, val series: List<Int>) {
    val seriesCsv: String get() = series.joinToString(",")
}

/** Outcome of importing a backup. [error] is non-null only when the file couldn't be read. */
data class ImportSummary(
    val sessionsAdded: Int = 0,
    val sessionsSkipped: Int = 0,
    val peopleAdded: Int = 0,
    val error: String? = null,
)
