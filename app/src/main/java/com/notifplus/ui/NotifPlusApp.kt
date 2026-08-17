package com.notifplus.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notifplus.R
import com.notifplus.presentation.AccessViewModel
import com.notifplus.ui.detail.NotificationDetailScreen
import com.notifplus.ui.history.HistoryScreen
import com.notifplus.ui.settings.AppRulesScreen
import com.notifplus.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
private object HistoryRoute

@Serializable
private object SettingsRoute

@Serializable
private object AppsRoute

@Serializable
private data class DetailRoute(val threadId: String)

private data class TopLevelDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: Any,
)

@Composable
fun NotifPlusApp(accessViewModel: AccessViewModel) {
    val navController = rememberNavController()
    val accessGranted by accessViewModel.accessGranted.collectAsStateWithLifecycle()
    val listenerHealth by accessViewModel.listenerHealth.collectAsStateWithLifecycle()
    val isIgnoringBatteryOptimizations by accessViewModel.isIgnoringBatteryOptimizations.collectAsStateWithLifecycle()
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route.orEmpty()
    val isDetailScreen = currentRoute.contains("DetailRoute")

    val destinations = listOf(
        TopLevelDestination(stringResource(R.string.history), Icons.Outlined.History, HistoryRoute),
        TopLevelDestination(stringResource(R.string.apps), Icons.Outlined.Apps, AppsRoute),
        TopLevelDestination(stringResource(R.string.settings), Icons.Outlined.Settings, SettingsRoute),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            AnimatedVisibility(
                visible = !isDetailScreen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentRoute.contains(destination.route::class.simpleName.orEmpty())
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    popUpTo(HistoryRoute) { saveState = true }
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HistoryRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HistoryRoute> {
                HistoryScreen(
                    accessGranted = accessGranted,
                    listenerHealth = listenerHealth,
                    onRequestAccess = { showDisclosure = true },
                    onRequestRebind = accessViewModel::requestRebind,
                    onOpenDetail = { id -> navController.navigate(DetailRoute(id)) },
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimization = accessViewModel::requestIgnoreBatteryOptimizations,
                )
            }
            composable<AppsRoute> {
                AppRulesScreen(
                    onRequestAccess = { showDisclosure = true },
                    onRequestRebind = accessViewModel::requestRebind,
                    accessGranted = accessGranted,
                    listenerHealth = listenerHealth,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimization = accessViewModel::requestIgnoreBatteryOptimizations,
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    accessGranted = accessGranted,
                    listenerHealth = listenerHealth,
                    onRequestAccess = { showDisclosure = true },
                    onRequestRebind = accessViewModel::requestRebind,
                    onOpenApps = { navController.navigate(AppsRoute) },
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimization = accessViewModel::requestIgnoreBatteryOptimizations,
                )
            }
            composable<DetailRoute> {
                NotificationDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.privacy_disclosure_title)) },
            text = { Text(stringResource(R.string.privacy_disclosure_body)) },
            confirmButton = {
                Button(onClick = {
                    showDisclosure = false
                    accessViewModel.openSettings()
                }) { Text(stringResource(R.string.open_notification_access)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }
}
