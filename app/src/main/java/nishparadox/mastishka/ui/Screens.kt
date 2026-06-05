package nishparadox.mastishka.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nishparadox.mastishka.MeditationViewModel
import nishparadox.mastishka.data.GongType
import nishparadox.mastishka.data.Person
import nishparadox.mastishka.data.Session
import nishparadox.mastishka.service.TimerState
import nishparadox.mastishka.service.formatClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Setup

@Composable
fun SetupScreen(
    vm: MeditationViewModel,
    onStart: () -> Unit,
    onHistory: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Mastishka", fontSize = 34.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
        // Only the sunflower is the theme toggle — tap it to flip light/dark.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Be happy :) ",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
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
        Spacer(Modifier.height(28.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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

        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Gong volume — ${(vm.gongVolume * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                }
                Slider(
                    value = vm.gongVolume,
                    onValueChange = { vm.updateGongVolume(it) },
                    valueRange = 0f..1f,
                )
                Spacer(Modifier.height(12.dp))
                Text("Gong sound", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GongType.entries.forEach { type ->
                        if (vm.gongType == type) {
                            Button(onClick = { vm.updateGongType(type) }) { Text(type.label) }
                        } else {
                            OutlinedButton(onClick = { vm.updateGongType(type) }) { Text(type.label) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.testGong() }) { Text("Test gong") }
                    OutlinedButton(onClick = { vm.stopTestGong() }) { Text("Stop") }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Begin sit", fontSize = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onHistory) {
            Icon(Icons.Filled.History, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Past sits")
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
fun SitScreen(state: TimerState, onEnd: () -> Unit) {
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
                    "REMAINING",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(formatClock(state.remainingMillis), fontSize = 72.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(64.dp))
            OutlinedButton(
                onClick = onEnd,
                modifier = Modifier.height(56.dp).width(200.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("End sit", fontSize = 18.sp) }
        }
    }
}

// ---------------------------------------------------------------- Metta

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MettaScreen(
    state: TimerState,
    people: List<Person>,
    selected: List<String>,
    onTogglePerson: (String) -> Unit,
    onAddPerson: (String) -> Unit,
    onDeletePerson: (Long) -> Unit,
    onSave: (calmness: Int, notes: String) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    var calmness by remember { mutableIntStateOf(3) }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("Metta Bhavana", fontSize = 26.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                TimeRow("Planned", formatClock(state.plannedMillis))
                TimeRow("Overtime", "+${formatClock(state.overtimeMillis)}")
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TimeRow("Total sat", formatClock(state.elapsedMillis), emphasize = true)
            }
        }

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

        Spacer(Modifier.height(24.dp))
        Text("Calmness / positivity — $calmness / 5", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = calmness.toFloat(),
            onValueChange = { calmness = it.toInt() },
            valueRange = 1f..5f,
            steps = 3,
        )

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
) {
    val selected = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

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
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            if (sessions.isEmpty()) {
                Text(
                    "No sits yet. Your finished sits will appear here.",
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
            sessions.forEach { s ->
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
                            Text(fmt.format(Date(s.startedAt)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text("Total ${formatClock(s.totalMillis)}  (planned ${formatClock(s.plannedMillis)}, +${formatClock(s.overtimeMillis)})")
                            Text("Calmness ${s.calmness}/5")
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
}

// ---------------------------------------------------------------- helpers

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
