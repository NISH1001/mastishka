package nishparadox.mastishka

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import nishparadox.mastishka.ui.HistoryScreen
import nishparadox.mastishka.ui.MastishkaTheme
import nishparadox.mastishka.ui.MettaScreen
import nishparadox.mastishka.ui.SetupScreen
import nishparadox.mastishka.ui.SitScreen

private enum class Screen { Setup, Sit, Metta, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MeditationViewModel = viewModel()
            MastishkaTheme(darkTheme = vm.darkTheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }
}

@Composable
private fun App(vm: MeditationViewModel) {
    val timer by vm.timerState.collectAsState()
    val people by vm.people.collectAsState(initial = emptyList())
    val sessions by vm.sessions.collectAsState(initial = emptyList())

    NotificationPermissionRequest()

    var screen by rememberSaveable {
        mutableStateOf(if (timer.running) Screen.Sit else Screen.Setup)
    }

    when (screen) {
        Screen.Setup -> SetupScreen(
            vm = vm,
            onStart = { vm.startSit(); screen = Screen.Sit },
            onHistory = { screen = Screen.History },
        )

        Screen.Sit -> SitScreen(
            state = timer,
            onEnd = { vm.endSit(); screen = Screen.Metta },
        )

        Screen.Metta -> MettaScreen(
            state = timer,
            practice = vm.meditationType.ifBlank { "Meditation" },
            people = people,
            selected = vm.selectedPeople,
            onTogglePerson = vm::togglePerson,
            onAddPerson = vm::addPerson,
            onDeletePerson = vm::deletePerson,
            onSave = { calmness, notes -> vm.saveSession(calmness, notes) { screen = Screen.Setup } },
            onDiscard = { vm.discardSit(); screen = Screen.Setup },
        )

        Screen.History -> HistoryScreen(
            sessions = sessions,
            onBack = { screen = Screen.Setup },
            onDelete = vm::deleteSessions,
        )
    }
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: the sit still runs without the notification */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
