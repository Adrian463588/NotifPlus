package com.notifplus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notifplus.R
import com.notifplus.domain.model.ListenerState
import com.notifplus.domain.model.NotificationListenerHealth

@Composable
fun AccessBanner(
    accessGranted: Boolean,
    listenerHealth: NotificationListenerHealth,
    onRequestAccess: () -> Unit,
    onRequestRebind: () -> Unit,
    modifier: Modifier = Modifier,
    isIgnoringBatteryOptimizations: Boolean = true,
    onRequestBatteryOptimization: () -> Unit = {},
) {
    val title = when {
        !accessGranted -> stringResource(R.string.access_disabled)
        listenerHealth.state == ListenerState.CONNECTED -> stringResource(R.string.access_enabled)
        listenerHealth.state == ListenerState.RECONNECTING -> stringResource(R.string.listener_reconnecting)
        else -> stringResource(R.string.listener_disconnected)
    }
    val hint = when {
        !accessGranted -> stringResource(R.string.access_required_hint)
        listenerHealth.state == ListenerState.CONNECTED -> stringResource(R.string.access_coverage_hint)
        else -> stringResource(R.string.listener_disconnected_hint)
    }

    val icon = when {
        !accessGranted -> Icons.Outlined.ErrorOutline
        listenerHealth.state == ListenerState.CONNECTED -> Icons.Outlined.CheckCircleOutline
        else -> Icons.Outlined.WarningAmber
    }

    val targetColor = when {
        !accessGranted -> MaterialTheme.colorScheme.errorContainer
        listenerHealth.state == ListenerState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    val targetContentColor = when {
        !accessGranted -> MaterialTheme.colorScheme.onErrorContainer
        listenerHealth.state == ListenerState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val containerColor by animateColorAsState(targetValue = targetColor, label = "bannerContainerColor")
    val contentColor by animateColorAsState(targetValue = targetContentColor, label = "bannerContentColor")

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                )

                listenerHealth.lastPostedAt?.let { timestamp ->
                    Text(
                        text = stringResource(R.string.listener_last_received, formatTimestamp(timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                listenerHealth.lastPersistedAt?.let { timestamp ->
                    Text(
                        text = stringResource(R.string.listener_last_saved, formatTimestamp(timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (!accessGranted) {
                    Button(
                        onClick = onRequestAccess,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.open_notification_access))
                    }
                } else if (listenerHealth.state != ListenerState.CONNECTED) {
                    OutlinedButton(
                        onClick = onRequestRebind,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reconnect_listener))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = accessGranted && !isIgnoringBatteryOptimizations,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryAlert,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.battery_opt_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.battery_opt_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FilledTonalButton(
                        onClick = onRequestBatteryOptimization,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(stringResource(R.string.battery_opt_button))
                    }
                }
            }
        }
    }
}
