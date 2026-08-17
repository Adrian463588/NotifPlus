package com.notifplus.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifplus.R
import com.notifplus.domain.model.NotificationListenerHealth
import com.notifplus.presentation.AppRuleUiModel
import com.notifplus.presentation.SettingsViewModel
import com.notifplus.ui.components.AccessBanner
import com.notifplus.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppRulesScreen(
    accessGranted: Boolean,
    listenerHealth: NotificationListenerHealth,
    onRequestAccess: () -> Unit,
    onRequestRebind: () -> Unit,
    modifier: Modifier = Modifier,
    isIgnoringBatteryOptimizations: Boolean = true,
    onRequestBatteryOptimization: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val rules by viewModel.appRules.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.apps)) })

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item {
                AccessBanner(
                    accessGranted = accessGranted,
                    listenerHealth = listenerHealth,
                    onRequestAccess = onRequestAccess,
                    onRequestRebind = onRequestRebind,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimization = onRequestBatteryOptimization,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.apps_rule_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onAppSearchQueryChanged,
                    label = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = { viewModel.onAppSearchQueryChanged("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.close))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (rules.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_apps),
                        message = stringResource(
                            if (searchQuery.isNotEmpty()) R.string.no_apps_filtered else R.string.no_apps,
                        ),
                    )
                }
            } else {
                items(rules, key = { it.packageName }) { rule ->
                    AppRuleCard(rule = rule, onToggle = viewModel::setAutoDismiss)
                }
            }
        }
    }
}

@Composable
private fun AppRuleCard(rule: AppRuleUiModel, onToggle: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val appIconDrawable by produceState<Drawable?>(initialValue = null, key1 = rule.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(rule.packageName) }.getOrNull()
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (appIconDrawable != null) {
                val bitmap = appIconDrawable!!.toBitmap(40, 40)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = rule.appLabel,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.appLabel,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle(rule.packageName, it) },
            )
        }
    }
}
