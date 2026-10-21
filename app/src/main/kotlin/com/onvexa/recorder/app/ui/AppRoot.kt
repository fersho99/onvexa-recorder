package com.onvexa.recorder.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onvexa.recorder.app.R
import com.onvexa.recorder.app.ui.ask.AskScreen
import com.onvexa.recorder.app.ui.detail.DetailScreen
import com.onvexa.recorder.app.ui.library.LibraryScreen
import com.onvexa.recorder.app.ui.record.RecordScreen
import com.onvexa.recorder.app.ui.settings.SettingsScreen
import com.onvexa.recorder.app.ui.tasks.TasksScreen
import com.onvexa.recorder.core.design.OnvexaPalette
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind

object Routes {
    const val LIBRARY = "library"
    const val ASK = "ask"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val RECORD = "record"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

private val topLevelRoutes = setOf(Routes.LIBRARY, Routes.ASK, Routes.TASKS, Routes.SETTINGS)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    fun selectTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = OnvexaPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route in topLevelRoutes) {
                BottomBar(
                    currentRoute = route,
                    onSelect = ::selectTab,
                    onRecord = { nav.navigate(Routes.RECORD) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpen = { id -> nav.navigate(Routes.detail(id)) })
            }
            composable(Routes.ASK) { AskScreen() }
            composable(Routes.TASKS) {
                TasksScreen(onOpenRecording = { id -> nav.navigate(Routes.detail(id)) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.RECORD) { RecordScreen(onClose = { nav.popBackStack() }) }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) {
                DetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onRecord: () -> Unit,
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = OnvexaPalette.PrimaryLight,
        selectedTextColor = OnvexaPalette.PrimaryLight,
        indicatorColor = Color.Transparent,
        unselectedIconColor = OnvexaPalette.TextMuted,
        unselectedTextColor = OnvexaPalette.TextMuted,
    )
    Column {
    HorizontalDivider(color = OnvexaPalette.Outline)
    NavigationBar(containerColor = OnvexaPalette.Surface, contentColor = OnvexaPalette.Text, tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = currentRoute == Routes.LIBRARY,
            onClick = { onSelect(Routes.LIBRARY) },
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_files)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ASK,
            onClick = { onSelect(Routes.ASK) },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_ask)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onRecord,
            icon = {
                RecordButton(
                    kind = RecordButtonKind.Record,
                    description = stringResource(R.string.nav_record),
                    onClick = onRecord,
                    size = 44.dp,
                )
            },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == Routes.TASKS,
            onClick = { onSelect(Routes.TASKS) },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_tasks)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { onSelect(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_settings)) },
            colors = colors,
        )
    }
    }
}
