package nishparadox.mastishka.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import nishparadox.mastishka.MainActivity
import nishparadox.mastishka.R
import nishparadox.mastishka.data.SitClock
import nishparadox.mastishka.data.volumeGain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Immutable snapshot of the running sit, observed by the UI. */
data class TimerState(
    val running: Boolean = false,
    val startedAtEpoch: Long = 0L,
    val plannedMillis: Long = 0L,
    val elapsedMillis: Long = 0L,
    val gongRung: Boolean = false,
    // Guided sits: the recording being played ("" = silent sit), whether the sit is paused,
    // and whether the recording could not be played (the sit then carries on as a silent one).
    val guideTitle: String = "",
    val paused: Boolean = false,
    val guideFailed: Boolean = false,
) {
    val guided: Boolean get() = guideTitle.isNotEmpty()
    val remainingMillis: Long get() = (plannedMillis - elapsedMillis).coerceAtLeast(0)
    val overtimeMillis: Long get() = (elapsedMillis - plannedMillis).coerceAtLeast(0)
    val inOvertime: Boolean get() = elapsedMillis >= plannedMillis
}

/**
 * Foreground service that owns the authoritative meditation clock. It counts down to the
 * planned duration, rings the gong once at zero (without requiring any interaction), then
 * keeps counting upward as "overtime" until the user ends the sit. A partial wake lock keeps
 * the CPU alive so the gong fires reliably even with the screen off.
 *
 * A guided sit additionally plays one of the user's recordings as media. Its length is the
 * planned duration, no gong rings at the end, and the sit can be paused: the clock counts only
 * time spent running.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var gongPlayer: MediaPlayer? = null
    // Written on the main thread, read by the tick coroutine.
    @Volatile private var clock = SitClock.startedAt(0L)
    private var guidePlayer: MediaPlayer? = null
    private var guidePrepared = false
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var gongVolume = 0.7f
    private var gongResId = R.raw.gong_medium

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val planned = intent.getLongExtra(EXTRA_PLANNED_MILLIS, 60 * 60 * 1000L)
                gongVolume = intent.getFloatExtra(EXTRA_VOLUME, 0.7f)
                gongResId = intent.getIntExtra(EXTRA_GONG_RES, R.raw.gong_medium)
                val startedAtEpoch = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                startSit(
                    planned,
                    startedAtEpoch,
                    intent.getStringExtra(EXTRA_GUIDE_URI),
                    intent.getStringExtra(EXTRA_GUIDE_TITLE).orEmpty(),
                )
            }
            ACTION_END -> endSit()
            ACTION_PAUSE -> pauseSit()
            ACTION_RESUME -> resumeSit()
            else -> { /* ignore */ }
        }
        return START_STICKY
    }

    private fun startSit(plannedMillis: Long, startedAtEpoch: Long, guideUri: String?, guideTitle: String) {
        if (_state.value.running) return
        clock = SitClock.startedAt(SystemClock.elapsedRealtime())
        val guided = !guideUri.isNullOrEmpty()
        _state.value = TimerState(
            running = true,
            startedAtEpoch = startedAtEpoch,
            plannedMillis = plannedMillis,
            elapsedMillis = 0L,
            gongRung = false,
            guideTitle = if (guided) guideTitle.ifBlank { "Guided" } else "",
        )

        acquireWakeLock()
        startSitForeground(NOTIFICATION_ID, buildNotification(_state.value))
        if (guided) startGuide(Uri.parse(guideUri))

        tickJob?.cancel()
        tickJob = scope.launch {
            var lastShownSecond = -1L
            while (isActive) {
                val elapsed = clock.elapsed(SystemClock.elapsedRealtime())
                var rungNow = false
                // update{}: the main thread also writes state (pause, guide events).
                _state.update { prev ->
                    rungNow = !prev.gongRung && elapsed >= prev.plannedMillis
                    prev.copy(elapsedMillis = elapsed, gongRung = prev.gongRung || rungNow)
                }
                // A guided sit ends on the recording's own closing — no gong — unless the
                // recording failed and the sit fell back to a plain timer.
                val now = _state.value
                if (rungNow && (!now.guided || now.guideFailed)) playGong()

                val sec = elapsed / 1000
                if (sec != lastShownSecond) {
                    lastShownSecond = sec
                    updateNotification(_state.value)
                }
                delay(200)
            }
        }
    }

    private fun endSit() {
        tickJob?.cancel()
        tickJob = null
        // Freeze the final elapsed time so the UI can read total/overtime, but mark stopped.
        val finalElapsed = clock.elapsed(SystemClock.elapsedRealtime())
        _state.update { it.copy(running = false, elapsedMillis = finalElapsed) }
        releaseWakeLock()
        releaseGong()
        releaseGuide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- Guided recording ----------

    private fun startGuide(uri: Uri) {
        val player = MediaPlayer()
        guidePrepared = false
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(this, uri)
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            val gain = volumeGain(gongVolume)
            player.setVolume(gain, gain)
            player.setOnPreparedListener { p ->
                if (guidePlayer !== p) return@setOnPreparedListener
                guidePrepared = true
                // The recording's real length is the planned duration; restart the clock so
                // the countdown and the audio begin together.
                val duration = p.duration.toLong()
                val wasPaused = clock.paused
                clock = SitClock.startedAt(SystemClock.elapsedRealtime())
                if (duration > 0) _state.update { it.copy(plannedMillis = duration, elapsedMillis = 0L, gongRung = false) }
                if (wasPaused || !requestFocus()) {
                    clock = clock.pause(SystemClock.elapsedRealtime())
                    _state.update { it.copy(paused = true) }
                    updateNotification(_state.value)
                } else {
                    p.start()
                }
            }
            player.setOnCompletionListener { p -> if (guidePlayer === p) releaseGuide() }
            player.setOnErrorListener { p, _, _ -> if (guidePlayer === p) onGuideFailed(); true }
            guidePlayer = player
            registerNoisyReceiver()
            player.prepareAsync()
        } catch (e: Exception) {
            guidePlayer = player
            onGuideFailed()
        }
    }

    /** The recording can't be played (moved, deleted, unsupported): carry on as a silent sit. */
    private fun onGuideFailed() {
        releaseGuide()
        if (clock.paused) clock = clock.resume(SystemClock.elapsedRealtime())
        _state.update { it.copy(guideFailed = true, paused = false) }
    }

    private fun releaseGuide() {
        guidePlayer?.let { runCatching { it.release() } }
        guidePlayer = null
        guidePrepared = false
        focusRequest?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focusRequest = null
        noisyReceiver?.let { runCatching { unregisterReceiver(it) } }
        noisyReceiver = null
    }

    private fun pauseSit() {
        val s = _state.value
        if (!s.running || !s.guided || s.guideFailed || s.paused) return
        val now = SystemClock.elapsedRealtime()
        clock = clock.pause(now)
        if (guidePrepared) guidePlayer?.let { p -> runCatching { if (p.isPlaying) p.pause() } }
        _state.update { it.copy(paused = true, elapsedMillis = clock.elapsed(now)) }
        updateNotification(_state.value)
    }

    private fun resumeSit() {
        val s = _state.value
        if (!s.running || !s.paused) return
        // Still preparing: the prepared callback starts playback once the flag is cleared.
        if (guidePlayer != null && guidePrepared && !requestFocus()) return
        clock = clock.resume(SystemClock.elapsedRealtime())
        if (guidePrepared) guidePlayer?.let { p -> runCatching { p.start() } }
        _state.update { it.copy(paused = false) }
        updateNotification(_state.value)
    }

    /** Take audio focus for the recording. Losing it (a call, another player) pauses the sit;
     *  it is never resumed automatically — the meditator resumes by hand. */
    private fun requestFocus(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pauseSit()
                }
            }
            .build()
            .also { focusRequest = it }
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Headphones unplugged / Bluetooth dropped: pause rather than blare from the speaker. */
    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = pauseSit()
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiver = receiver
    }

    // ---------- Gong ----------

    private fun playGong() {
        releaseGong()
        // On headphones, play on the media usage so it reaches the ears (the alarm stream skips
        // Bluetooth). Otherwise keep the alarm stream — loud, and rings through Do Not Disturb —
        // and max its volume only while the gong plays (the in-app slider stays the loudness knob).
        val onHeadphones = headphonesConnected(getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        val player = MediaPlayer().apply {
            setAudioAttributes(gongAudioAttributes(onHeadphones))
        }
        val afd = resources.openRawResourceFd(gongResId) ?: return
        afd.use {
            player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
        val gain = volumeGain(gongVolume)
        player.setVolume(gain, gain)
        player.setOnPreparedListener { if (!onHeadphones) raiseAlarmVolume(); it.start() }
        player.setOnCompletionListener { restoreAlarmVolume(); it.release(); if (gongPlayer === it) gongPlayer = null }
        player.prepareAsync()
        gongPlayer = player
    }

    private fun releaseGong() {
        gongPlayer?.let { runCatching { it.release() } }
        gongPlayer = null
        restoreAlarmVolume()
    }

    // Max the alarm stream only while the gong sounds, remembering the user's level so it can
    // be put back. Touches the alarm *volume* only — never alarm schedules. Wrapped in
    // runCatching since setStreamVolume can throw on restricted devices.
    private var savedAlarmVolume: Int? = null

    private fun raiseAlarmVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (savedAlarmVolume == null) savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
    }

    private fun restoreAlarmVolume() {
        val saved = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        }
    }

    // ---------- Wake lock ----------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mastishka:sit").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // safety cap: 6 hours
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---------- Notification ----------

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the running meditation sit."
                setSound(null, null)
                enableVibration(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: TimerState): Notification {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            state.paused -> "Paused — ${formatClock(state.remainingMillis)} remaining"
            state.inOvertime -> "Overtime +${formatClock(state.overtimeMillis)} — still sitting"
            else -> "${formatClock(state.remainingMillis)} remaining"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Meditation in progress")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startSitForeground(id: Int, notification: Notification) {
        // The "specialUse" FGS type only exists on API 34+; older devices use the plain form.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun updateNotification(state: TimerState) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(state))
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        releaseGong()
        releaseGuide()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "nishparadox.mastishka.START"
        const val ACTION_END = "nishparadox.mastishka.END"
        const val EXTRA_PLANNED_MILLIS = "planned_millis"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_GONG_RES = "gong_res"
        const val EXTRA_STARTED_AT = "started_at"
        const val ACTION_PAUSE = "nishparadox.mastishka.PAUSE"
        const val ACTION_RESUME = "nishparadox.mastishka.RESUME"
        const val EXTRA_GUIDE_URI = "guide_uri"
        const val EXTRA_GUIDE_TITLE = "guide_title"

        private const val CHANNEL_ID = "meditation_timer"
        private const val NOTIFICATION_ID = 1

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        /** Clear the frozen state after a sit has been saved/discarded. */
        fun reset() {
            _state.value = TimerState()
        }

        /** Start a sit. With [guideUri] set it is a guided sit: that recording plays as media
         *  and [plannedMillis] should be its length (corrected once the player has read it). */
        fun start(
            context: Context,
            plannedMillis: Long,
            volume: Float,
            gongResId: Int,
            guideUri: String? = null,
            guideTitle: String = "",
        ) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLANNED_MILLIS, plannedMillis)
                putExtra(EXTRA_VOLUME, volume)
                putExtra(EXTRA_GONG_RES, gongResId)
                putExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                if (!guideUri.isNullOrEmpty()) {
                    putExtra(EXTRA_GUIDE_URI, guideUri)
                    putExtra(EXTRA_GUIDE_TITLE, guideTitle)
                }
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, TimerService::class.java).apply { action = ACTION_RESUME })
        }

        fun end(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply { action = ACTION_END }
            context.startService(intent)
        }
    }
}

/**
 * True if headphones (wired, USB, or Bluetooth) are an active output. The alarm stream the gong
 * normally uses won't route to Bluetooth — alarms deliberately stay on the phone speaker so they
 * aren't missed — so a meditator wearing earbuds would hear nothing. When this is true we play the
 * gong on the media usage instead, which follows the active output into the headphones.
 */
fun headphonesConnected(am: AudioManager): Boolean =
    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

/** Build the gong's audio attributes: media usage on headphones (so it reaches the ears),
 *  otherwise the alarm usage (loud, and rings through Do Not Disturb). */
fun gongAudioAttributes(onHeadphones: Boolean): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(if (onHeadphones) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

/** Formats milliseconds as H:MM:SS (or M:SS under an hour). */
fun formatClock(millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
