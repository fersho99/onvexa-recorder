package com.onvexa.recorder.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.onvexa.recorder.wear.recorder.RecorderPhase
import com.onvexa.recorder.wear.ui.common.LocalAmbient
import com.onvexa.recorder.wear.ui.detail.DetailScreen
import com.onvexa.recorder.wear.ui.home.HomeScreen
import com.onvexa.recorder.wear.ui.list.RecordingsScreen
import com.onvexa.recorder.wear.ui.settings.SettingsScreen
import com.onvexa.recorder.wear.ui.record.AmbientRecording
import com.onvexa.recorder.wear.ui.record.ConfirmStopScreen
import com.onvexa.recorder.wear.ui.record.RecordScreen
import com.onvexa.recorder.wear.ui.record.SavedScreen

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val CONFIRM = "confirm"
    const val SAVED = "saved/{ms}"
    const val RECORDINGS = "recordings"
    const val DETAIL = "detail/{id}"
    const val SETTINGS = "settings"

    fun saved(ms: Long) = "saved/$ms"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun WearNavHost(startDestination: String, shell: ShellViewModel = hiltViewModel()) {
    val recorder by shell.recorderState.collectAsStateWithLifecycle()
    val ambient = LocalAmbient.current

    val nav = rememberSwipeDismissableNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val goHome: () -> Unit = {
        nav.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }
    val goSaved: (Long) -> Unit = { ms ->
        nav.navigate(Routes.saved(ms)) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize()) {
    // En la pantalla de grabar, volver pide confirmación en lugar de salir con un deslizamiento.
    SwipeDismissableNavHost(
        navController = nav,
        startDestination = startDestination,
        userSwipeEnabled = currentRoute != Routes.RECORD,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onRecord = { nav.navigate(Routes.RECORD) },
                onRecordings = { nav.navigate(Routes.RECORDINGS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(onRequestExit = { if (recorder.phase == RecorderPhase.Idle) goHome() else nav.navigate(Routes.CONFIRM) }, onSaved = goSaved)
        }
        composable(Routes.CONFIRM) {
            ConfirmStopScreen(onKeepRecording = { nav.popBackStack() }, onSaved = goSaved)
        }
        composable(Routes.RECORDINGS) {
            RecordingsScreen(onOpen = { id -> nav.navigate(Routes.detail(id)) })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { DetailScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(
            route = Routes.SAVED,
            arguments = listOf(navArgument("ms") { type = NavType.LongType }),
        ) { entry ->
            SavedScreen(durationMs = entry.arguments?.getLong("ms") ?: 0L, onDone = goHome)
        }
    }
    // Mientras se graba y el reloj está en ambiente, una vista atenuada cubre todo lo demás.
    if (ambient && recorder.phase != RecorderPhase.Idle) {
        AmbientRecording(recorder.elapsedMs, paused = recorder.phase == RecorderPhase.Paused)
    }
    }
}
