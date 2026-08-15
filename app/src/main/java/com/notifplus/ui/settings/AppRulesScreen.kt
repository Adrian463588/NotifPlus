package com.notifplus.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notifplus.R
import com.notifplus.domain.model.NotificationListenerHealth
import com.notifplus.presentation.AppRuleUiModel
import com.notifplus.presentation.SettingsViewModel
import com.notifplus.ui.components.AccessBanner
import com.notifplus.ui.components.EmptyState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppRulesScreen(
    accessGranted: Boolean,
    listenerHealth: NotificationListenerHealth,
    onRequestAccess: () -> Unit,
    onRequestRebind: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val rules by viewModel.appRules.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.apps)) })
        AccessBanner(accessGranted, listenerHealth, onRequestAccess, onRequestRebind, Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.apps_rule_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (rules.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_apps),
                message = "You don't have any apps configured for rules."
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(rules, key = { it.packageName }) { rule ->
                    AppRuleRow(rule, viewModel::setAutoDismiss)
                }
            }
        }
    }
}

@Composable
private fun AppRuleRow(rule: AppRuleUiModel, onToggle: (String, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(rule.packageName, modifier = Modifier.weight(1f))
        Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule.packageName, it) })
    }
}
