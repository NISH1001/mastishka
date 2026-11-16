package nishparadox.mastishka.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import nishparadox.mastishka.BuildConfig
import nishparadox.mastishka.HrSummary
import nishparadox.mastishka.ImportSummary
import nishparadox.mastishka.MeditationViewModel
import nishparadox.mastishka.data.GongType
import nishparadox.mastishka.data.GROUP_SITTINGS_URL
import nishparadox.mastishka.data.Granularity
import nishparadox.mastishka.data.Person
import nishparadox.mastishka.data.Recording
import nishparadox.mastishka.data.Session
import nishparadox.mastishka.service.TimerState
import nishparadox.mastishka.service.formatClock
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Setup

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    vm: MeditationViewModel,
    onStart: () -> Unit,
    onHistory: () -> Unit,
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> vm.onHealthPermissionResult(granted) }
    // Returning from Health Connect settings → re-check whether permission is now granted.
    val hcSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refreshHealthConnect() }
    LaunchedEffect(Unit) { vm.refreshHealthConnect() }
    // A failed download or add: say so once.
    LaunchedEffect(vm.guideMessage) {
        vm.guideMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearGuideMessage()
        }
    }
    // Guided sit about to start with the recording inaudible: say why, let them start anyway.
    var silenceWarning by remember { mutableStateOf<String?>(null) }
    silenceWarning?.let { message ->
        AlertDialog(
            onDismissRequest = { silenceWarning = null },
            title = { Text("You won't hear the recording") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { silenceWarning = null; onStart() }) { Text("Begin anyway") }
            },
            dismissButton = {
                TextButton(onClick = { silenceWarning = null }) { Text("Cancel") }
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scroll)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Tiny info button in the top corner — opens an about/version dialog.
        var showVersionInfo by remember { mutableStateOf(false) }
        if (showVersionInfo) {
            AlertDialog(
                onDismissRequest = { showVersionInfo = false },
                confirmButton = {
                    TextButton(onClick = { showVersionInfo = false }) { Text("Close") }
                },
                title = { Text("मस्तिष्क · Mastishka") },
                text = { Text("Mettā to you 🙏\n\nVersion ${BuildConfig.VERSION_NAME}") },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showVersionInfo = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "About Mastishka",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Tap the title to flip the heading between Nepali (मस्तिष्क) and English (Mastishka).
        // Fixed-height centered box so the taller Devanagari glyphs don't reflow the column
        // (and nudge the scroll) when toggling.
        Box(
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { vm.toggleNepaliTitle() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (vm.nepaliTitle) "मस्तिष्क" else "Mastishka",
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Two separate taps: the greeting text is an easter egg (flips wording), the
        // sunflower is the theme toggle (flips light/dark).
        var greetingMetta by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (greetingMetta) "Mettā to you :) " else "Be happy :) ",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { greetingMetta = !greetingMetta }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Text(
                "🌻",
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { vm.toggleTheme() }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        // Silent sit (timer + gong) or guided sit (one of the user's recordings).
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(false to "Silent", true to "Guided").forEachIndexed { index, (guided, label) ->
                SegmentedButton(
                    selected = vm.guidedMode == guided,
                    onClick = { vm.updateGuidedMode(guided) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) { Text(label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Text("Practice (optional)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Vipassana", "Anapana", "Focused Breathing", "Concentration").forEach { t ->
                        SelectableChip(
                            label = t,
                            selected = vm.meditationType == t,
                            onTap = { vm.updateMeditationType(t) },
                            onLongPress = {},
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vm.meditationType.isBlank()) "This sit will be saved as “Meditation”"
                    else "This sit will be saved as “${vm.meditationType}”",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (vm.guidedMode) {
            GuidedCard(
                recordings = vm.recordings,
                selectedId = vm.selectedRecordingId,
                progress = vm.downloadProgress,
                onSelect = vm::selectRecording,
                onAdd = vm::addRecording,
                onRemove = vm::removeRecording,
            )
        } else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sit duration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                DurationPicker(vm.durationMinutes) { vm.setDuration(it) }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(10 to "10m", 30 to "30m", 45 to "45m", 60 to "1h").forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { vm.setDuration(minutes) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val warning = if (vm.guidedMode) vm.guidedSilenceWarning() else null
                if (warning != null) silenceWarning = warning else onStart()
            },
            enabled = vm.canBegin,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            // While the chosen recording downloads, the play icon is a progress ring.
            val downloading = if (vm.guidedMode) vm.downloadProgress[vm.selectedRecordingId] else null
            if (downloading != null) {
                CircularProgressIndicator(
                    progress = { downloading },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Downloading ${(downloading * 100).toInt()}%", fontSize = 18.sp)
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.guidedMode && vm.selectedRecording == null) "Choose a recording" else "Begin sit",
                    fontSize = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Volume — ${(vm.gongVolume * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "For the gong, and for the recording in a guided sit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Slider(
                    value = vm.gongVolume,
                    onValueChange = { vm.updateGongVolume(it) },
                    valueRange = 0f..1f,
                )
                Spacer(Modifier.height(12.dp))
                Text("Gong sound", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                // Single-choice segmented control: equal-width segments that share the row width,
                // so labels can't overflow/wrap even at large font scales (e.g. Samsung One UI).
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GongType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = vm.gongType == type,
                            onClick = { vm.updateGongType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = GongType.entries.size),
                        ) { Text(type.label, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    FilledTonalButton(onClick = { vm.testGong() }) { Text("Test gong") }
                    OutlinedButton(onClick = { vm.stopTestGong() }) { Text("Stop") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        when {
            vm.hcConnected -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "✓ Syncing to Health Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = {
                    vm.syncAllSessions { count ->
                        val msg = when {
                            count < 0 -> "Connect to Health Connect first"
                            else -> "Synced $count sit${if (count == 1) "" else "s"} to Health Connect"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = "Sync past sits to Health Connect",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            vm.hcAvailable -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(onClick = {
                    runCatching { hcLauncher.launch(vm.hcPermissions) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                "Couldn't open Health Connect: ${it.javaClass.simpleName}: ${it.message}",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                }) {
                    Text("Connect to Health Connect")
                }
                TextButton(onClick = {
                    val perApp = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, "nishparadox.mastishka")
                    val home = Intent("androidx.health.connect.action.HEALTH_CONNECT_SETTINGS")
                    runCatching { hcSettingsLauncher.launch(perApp) }
                        .recoverCatching { hcSettingsLauncher.launch(home) }
                        .onFailure {
                            Toast.makeText(context, "Couldn't open Health Connect settings", Toast.LENGTH_LONG).show()
                        }
                }) { Text("Or grant in Health Connect settings") }
            }
            vm.hcUpdateRequired -> Text(
                "Update Health Connect to sync your sits",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            else -> Text(
                "Health Connect not available on this device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = vm.monitorHeartRate && vm.hcConnected,
                onCheckedChange = { vm.updateMonitorHeartRate(it) },
                enabled = vm.hcConnected,
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "Monitor heart rate",
                    color = if (vm.hcConnected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                )
                if (!vm.hcConnected) {
                    Text(
                        "Connect to Health Connect to enable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onHistory) {
            Icon(Icons.Filled.History, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Past sits")
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Guided mode's stand-in for the duration card: built-in recordings (downloaded once, on tap,
 * from their publisher) and any the user added, one to choose. Long-press removes one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuidedCard(
    recordings: List<Recording>,
    selectedId: String,
    progress: Map<String, Float>,
    onSelect: (String) -> Unit,
    onAdd: (Uri) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAdd(uri)
    }
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    var removing by remember { mutableStateOf<Recording?>(null) }
    removing?.let { r ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from this phone?") },
            text = {
                Text(
                    if (r.builtIn) "“${r.title}” will be deleted. You can download it again any time."
                    else "“${r.title}” will be deleted from Mastishka. Your original file is not touched."
                )
            },
            confirmButton = { TextButton(onClick = { onRemove(r.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
            Text(
                "Guided meditation",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.height(6.dp))
            recordings.forEach { r ->
                val downloading = progress[r.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onSelect(r.id) },
                            onLongClick = { if (r.downloaded) removing = r },
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = r.id == selectedId, onClick = { onSelect(r.id) })
                    Text(r.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    when {
                        // Downloading: a ring that fills, with the percentage beside it.
                        downloading != null -> {
                            Text("${(downloading * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = faint)
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                progress = { downloading },
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                        // On the phone: its length.
                        r.downloaded -> Text(
                            if (r.durationMillis > 0) formatClock(r.durationMillis) else "–",
                            style = MaterialTheme.typography.labelLarge,
                            color = faint,
                        )
                        // Not yet downloaded: how big it is, and a download icon.
                        else -> {
                            Text(
                                "${formatClock(r.durationMillis)} · ${"%.0f".format(r.sizeBytes / 1_048_576.0)} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = faint,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // The 1-hour Vipassana sittings need the old-student login, so the app can't fetch
            // them: open the page, save the file there, then add it below.
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GROUP_SITTINGS_URL))) }
                    .onFailure { Toast.makeText(context, "No browser to open dhamma.org", Toast.LENGTH_LONG).show() }
            }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("1-hour Vipassana group sitting (old students)", textAlign = TextAlign.Start)
            }
            TextButton(onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add your own recording")
            }
        }
    }
}

@Composable
private fun DurationPicker(totalMinutes: Int, onChange: (Int) -> Unit) {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Stepper(label = "hr", value = hours, onDelta = { onChange((totalMinutes + it * 60).coerceAtLeast(1)) })
        Stepper(label = "min", value = minutes, onDelta = { onChange((totalMinutes + it).coerceAtLeast(1)) })
    }
}

@Composable
private fun Stepper(label: String, value: Int, onDelta: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onDelta(+1) }) { Icon(Icons.Filled.Add, contentDescription = "increase $label") }
        Text("%02d".format(value), fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { onDelta(-1) }) { Icon(Icons.Filled.Remove, contentDescription = "decrease $label") }
    }
}

// ---------------------------------------------------------------- Sit

@Composable
fun SitScreen(state: TimerState, onEnd: () -> Unit, onPause: () -> Unit = {}, onResume: () -> Unit = {}) {
    KeepScreenOn()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.inOvertime) {
                Text(
                    "OVERTIME",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("+${formatClock(state.overtimeMillis)}", fontSize = 64.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Planned ${formatClock(state.plannedMillis)} reached. Keep sitting as long as you like.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    if (state.paused) "PAUSED" else "REMAINING",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(formatClock(state.remainingMillis), fontSize = 72.sp, fontWeight = FontWeight.Light)
            }
            if (state.guided) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.guideFailed) "Couldn't play “${state.guideTitle}” — continuing as a silent sit"
                    else state.guideTitle,
                    textAlign = TextAlign.Center,
                    color = if (state.guideFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            // Pause exists only while a recording is (or could be) playing — not in overtime.
            val pausable = state.guided && !state.guideFailed && (state.paused || !state.inOvertime)
            Spacer(Modifier.height(if (pausable) 48.dp else 72.dp))
            if (pausable) {
                FilledTonalButton(
                    onClick = if (state.paused) onResume else onPause,
                    modifier = Modifier.fillMaxWidth(0.72f).height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.paused) "Resume" else "Pause", fontSize = 18.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
            OutlinedButton(
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth(0.72f).height(72.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("End sit", fontSize = 20.sp) }
        }
    }
}

// ---------------------------------------------------------------- Metta

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MettaScreen(
    state: TimerState,
    practice: String,
    monitorHeartRate: Boolean,
    hr: HrSummary?,
    hrLoading: Boolean,
    onLoadHeartRate: () -> Unit,
    people: List<Person>,
    selected: List<String>,
    onTogglePerson: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onDeletePerson: (Long) -> Unit,
    onSave: (calmness: Int, notes: String) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val isVipassana = practice == "Vipassana"
    var calmness by remember { mutableIntStateOf(2) }
    var notes by remember { mutableStateOf("") }
    var newPerson by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Person?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            notes = if (notes.isBlank()) spoken else "$notes $spoken"
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text(
            if (isVipassana) "Metta Bhavana" else "After your sit",
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                TimeRow("Planned", formatClock(state.plannedMillis))
                TimeRow("Overtime", "+${formatClock(state.overtimeMillis)}")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TimeRow("Total sat", formatClock(state.elapsedMillis), emphasize = true)
            }
        }

        if (monitorHeartRate) {
            LaunchedEffect(Unit) { onLoadHeartRate() }
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Heart rate", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onLoadHeartRate) {
                            Icon(Icons.Filled.Sync, contentDescription = "Refresh heart rate", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    when {
                        hr != null -> {
                            Text("avg ${hr.avg} · ${hr.min}–${hr.max} bpm", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            HeartRateChart(hr.series, Modifier.height(120.dp))
                        }
                        hrLoading -> Text(
                            "Reading heart rate…",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        else -> Text(
                            "No heart-rate data yet — your band may still be syncing. Tap ↻ to refresh.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        if (isVipassana) {
        Spacer(Modifier.height(24.dp))
        Text("Who did you send metta to?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { person ->
                SelectableChip(
                    label = person.name,
                    selected = selected.contains(person.name),
                    onTap = { onTogglePerson(person.name) },
                    onLongPress = { pendingDelete = person },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPerson,
                onValueChange = { newPerson = it },
                label = { Text("Add a person") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                onAddPerson(newPerson)
                newPerson = ""
            }) { Text("Add") }
        }
        Text(
            "Tip: long-press a name to remove it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        } // end Vipassana-only metta section

        Spacer(Modifier.height(24.dp))
        Text("Calmness after the sit", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Lower", 2 to "Neutral", 3 to "Higher").forEach { (value, label) ->
                val mod = Modifier.weight(1f)
                val pad = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                if (calmness == value) {
                    Button(onClick = { calmness = value }, modifier = mod, contentPadding = pad) { Text(label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { calmness = value }, modifier = mod, contentPadding = pad) { Text(label, maxLines = 1) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Notes & tags", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("Reflections, intentions, tags…") },
            trailingIcon = {
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your reflection")
                    }
                    runCatching { voiceLauncher.launch(intent) }.onFailure {
                        Toast.makeText(context, "No voice input available on this device", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.Mic, contentDescription = "Dictate notes") }
            },
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onSave(calmness, notes) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Save sit", fontSize = 18.sp) }
        TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard") }
    }

    pendingDelete?.let { person ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${person.name}?") },
            confirmButton = {
                TextButton(onClick = { onDeletePerson(person.id); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(
            value,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Text(label, color = fg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

// ---------------------------------------------------------------- History

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onBack: () -> Unit,
    onDelete: (List<Long>) -> Unit,
    heartRateEnabled: Boolean,
    onRefreshHeartRate: () -> Unit,
    onExport: (Uri, (Boolean) -> Unit) -> Unit,
    onImport: (Uri, (ImportSummary) -> Unit) -> Unit,
    syncEnabled: Boolean,
    onSetupSync: (Uri, (String) -> Unit) -> Unit,
    onDisableSync: () -> Unit,
    thisDeviceId: String,
    onRefreshDrive: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    // null = All devices; "" = this phone; otherwise a linked device id.
    var deviceFilter by remember { mutableStateOf<String?>(null) }
    val selecting = selected.isNotEmpty()

    // Auto-pull heart rate + any new sits from other devices whenever History opens.
    LaunchedEffect(Unit) {
        if (heartRateEnabled) onRefreshHeartRate()
        onRefreshDrive()
    }

    // Storage Access Framework: the user picks where to write / which file to read. No
    // storage permission needed, and the granted Uri is scoped to just that document.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExport(uri) { ok ->
            Toast.makeText(
                context,
                if (ok) "Backup saved" else "Couldn't save backup",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImport = uri }
    // Drive sync setup — the one manual act, ever: grant the Drive → nishparadox/mastishka
    // folder. The app finds/creates sits/ inside, writes its own device file, and discovers
    // every other device's sits automatically from then on.
    val syncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onSetupSync(uri) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var dateFilter by rememberSaveable { mutableStateOf(DateFilter.ALL) }
    var customRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showRangePicker by remember { mutableStateOf(false) }

    // Device + date filters compose; both apply to both tabs.
    val devices = remember(sessions) { sessions.map { it.device }.distinct().sorted() }
    val deviceShown =
        if (deviceFilter == null) sessions else sessions.filter { it.device == deviceFilter }
    val earliest = remember(deviceShown) {
        deviceShown.minOfOrNull { it.startedAt }
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
    }
    val range = when (dateFilter) {
        DateFilter.D7 -> today.minusDays(6) to today
        DateFilter.D30 -> today.minusDays(29) to today
        DateFilter.D90 -> today.minusDays(89) to today
        DateFilter.YEAR -> today.minusDays(364) to today
        DateFilter.ALL -> earliest to today
        DateFilter.CUSTOM -> customRange ?: (earliest to today)
    }
    val from = range.first
    val rangeTo = range.second
    val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs = rangeTo.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val shown = deviceShown.filter { it.startedAt in fromMs until toMs }
    // Bar granularity follows the span of the visible range.
    val granularity = remember(from, rangeTo) {
        val days = ChronoUnit.DAYS.between(from, rangeTo) + 1
        when {
            days <= 14 -> Granularity.DAY
            days <= 180 -> Granularity.WEEK
            else -> Granularity.MONTH
        }
    }

    fun toggle(id: Long) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selected.size} selected" else "Past sits") },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        if (heartRateEnabled) {
                            IconButton(onClick = {
                                onRefreshHeartRate()
                                Toast.makeText(context, "Refreshing heart rate…", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.Sync, contentDescription = "Refresh heart rate")
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Export data…") },
                                onClick = {
                                    menuOpen = false
                                    // e.g. mastishka-backup-june-06-2026-1432.json — timestamped
                                    // (and filename-safe) so each export is a distinct file.
                                    val name = SimpleDateFormat(
                                        "'mastishka-backup-'MMMM-dd-yyyy-HHmm'.json'", Locale.US,
                                    ).format(Date()).lowercase(Locale.US)
                                    exportLauncher.launch(name)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Import data…") },
                                onClick = {
                                    menuOpen = false
                                    // */* so .jsonl sit files (no registered MIME; Drive often
                                    // reports octet-stream) are pickable alongside .json backups.
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                            )
                            HorizontalDivider()
                            if (syncEnabled) {
                                DropdownMenuItem(
                                    text = { Text("Change Drive folder…") },
                                    onClick = {
                                        menuOpen = false
                                        syncLauncher.launch(null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Turn off Drive sync") },
                                    onClick = {
                                        menuOpen = false
                                        onDisableSync()
                                        Toast.makeText(context, "Drive sync off", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Set up Drive sync…") },
                                    onClick = {
                                        menuOpen = false
                                        syncLauncher.launch(null)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // One quiet filter row: two dropdown pills (date range · device).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val rangeFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
                FilterPill(
                    text = if (dateFilter == DateFilter.CUSTOM)
                        "${rangeFmt.format(from)} – ${rangeFmt.format(rangeTo)}"
                    else dateFilter.label
                ) { dismiss ->
                    DateFilter.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label) },
                            onClick = {
                                dismiss()
                                if (f == DateFilter.CUSTOM) showRangePicker = true
                                else dateFilter = f
                            },
                        )
                    }
                }
                if (devices.size > 1) {
                    FilterPill(
                        text = when (deviceFilter) {
                            null -> "All devices"
                            "" -> thisDeviceId.ifEmpty { "this phone" }
                            else -> deviceFilter!!
                        }
                    ) { dismiss ->
                        DropdownMenuItem(
                            text = { Text("All devices") },
                            onClick = { dismiss(); deviceFilter = null },
                        )
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.ifEmpty { thisDeviceId.ifEmpty { "this phone" } }) },
                                onClick = { dismiss(); deviceFilter = device },
                            )
                        }
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Sits") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Insights") })
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                if (tab == 1) {
                    InsightsTab(
                        filtered = shown,
                        allSessions = sessions,
                        from = from,
                        to = rangeTo,
                        granularity = granularity,
                        zone = zone,
                    )
                } else {
            if (sessions.isEmpty()) {
                Text(
                    "No sits yet. Your finished sits will appear here.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else if (shown.isEmpty()) {
                Text(
                    "Nothing in this range.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    "Tap a sit to select, then delete.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(4.dp))
            }

            val fmt = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()) }
            shown.forEach { s ->
                val isSelected = selected.contains(s.id)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { toggle(s.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { toggle(s.id) })
                        Column(Modifier.padding(end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                            Text(s.type, style = MaterialTheme.typography.titleMedium)
                            Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (s.guide.isNotEmpty()) {
                                Text(
                                    "Guided · ${s.guide}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            if (s.device.isNotEmpty()) {
                                Text(
                                    s.device,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Total ${formatClock(s.totalMillis)}  (planned ${formatClock(s.plannedMillis)}, +${formatClock(s.overtimeMillis)})")
                            Text("Calmness: ${calmnessLabel(s.calmness)}")
                            if (s.hasHeartRate) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "❤ ${s.hrAvg} bpm  (${s.hrMin}–${s.hrMax})",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                val pts = s.hrSeries.split(",").mapNotNull { it.toIntOrNull() }
                                if (pts.size >= 2) {
                                    Spacer(Modifier.height(4.dp))
                                    HeartRateChart(pts, Modifier.height(40.dp))
                                }
                            }
                            if (s.people.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Metta: ${s.people.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                            if (s.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(s.notes, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        // Picker millis are UTC-midnight based.
                        customRange =
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate() to
                                Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        dateFilter = DateFilter.CUSTOM
                    }
                    showRangePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(pickerState, modifier = Modifier.weight(1f))
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} sit${if (selected.size == 1) "" else "s"}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(selected.toList())
                    selected.clear()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import backup?") },
            text = { Text("Sits and people from the file are merged into your history. Anything already present is skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    onImport(uri) { summary ->
                        val msg = when {
                            summary.error != null -> summary.error
                            summary.sessionsAdded == 0 -> "Already up to date — nothing new to import."
                            else -> "Imported ${summary.sessionsAdded} sit${if (summary.sessionsAdded == 1) "" else "s"}" +
                                (if (summary.sessionsSkipped > 0) ", ${summary.sessionsSkipped} already present" else "") +
                                (if (summary.peopleAdded > 0) " · ${summary.peopleAdded} new ${if (summary.peopleAdded == 1) "person" else "people"}" else "")
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------- helpers

/** History date filter presets; CUSTOM opens the range picker. */
private enum class DateFilter(val label: String) {
    ALL("All time"),
    D7("Last 7 days"),
    D30("Last 30 days"),
    D90("Last 90 days"),
    YEAR("Last year"),
    CUSTOM("Custom range…"),
}

/** A quiet dropdown pill for the History filter row. */
@Composable
private fun FilterPill(text: String, menuItems: @Composable (dismiss: () -> Unit) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable { open = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(text, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menuItems { open = false }
        }
    }
}

private fun calmnessLabel(v: Int): String = when (v) {
    1 -> "lower"
    2 -> "neutral"
    3 -> "higher"
    else -> "$v/5" // older sits used a 1–5 scale
}

@Composable
private fun HeartRateChart(series: List<Int>, modifier: Modifier = Modifier) {
    if (series.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    val mn = series.min()
    val mx = series.max()
    val range = (mx - mn).coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth()) {
        val stepX = size.width / (series.size - 1)
        val path = Path()
        series.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - mn).toFloat() / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
