package com.notifplus.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notifplus.R
import com.notifplus.domain.model.AttachmentReadStatus
import com.notifplus.domain.model.NotificationSnapshot
import com.notifplus.domain.model.NotificationSnapshotWithRelations
import com.notifplus.presentation.DetailViewModel
import com.notifplus.ui.components.AttachmentImagePreview
import com.notifplus.ui.components.DetailField
import com.notifplus.ui.components.FullscreenImageDialog
import com.notifplus.ui.components.formatTimestamp
import com.notifplus.ui.components.removalLabel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotificationDetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.notification_detail)) },
            navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(Icons.Outlined.Star, contentDescription = stringResource(R.string.favorite))
                }
            },
        )
        detail?.let { item ->
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                item { Text(item.summary.appLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                item { Text(item.summary.latestTitle.ifBlank { stringResource(R.string.untitled_notification) }, style = MaterialTheme.typography.headlineSmall) }
                item { Text(stringResource(R.string.package_format, item.summary.packageName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Text(
                        stringResource(
                            R.string.status_format,
                            if (item.summary.isActive) stringResource(R.string.active) else removalLabel(item.summary),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item { Text(stringResource(R.string.revision_count_format, item.summary.revisionCount), style = MaterialTheme.typography.bodyMedium) }
                item { DetailField(stringResource(R.string.removal_reason_code), item.summary.removalReasonCode?.toString().orEmpty()) }
                item { Text(stringResource(R.string.auto_dismiss_status_format, item.summary.autoDismissStatus.name), style = MaterialTheme.typography.bodyMedium) }
                items(item.snapshots, key = { it.snapshot.snapshotId }) { snapshot ->
                    SnapshotTimelineCard(snapshot)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::toggleRead) { Text(stringResource(R.string.toggle_read)) }
                        OutlinedButton(onClick = viewModel::delete) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        } ?: Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.notification_not_found), modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun SnapshotTimelineCard(relation: NotificationSnapshotWithRelations) {
    val snapshot = relation.snapshot
    var fullscreenAttachmentId by rememberSaveable { mutableStateOf<String?>(null) }
    Card(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.snapshot_header,
                        snapshot.captureOrigin.name,
                        formatTimestamp(snapshot.capturedAt),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                DetailField(stringResource(R.string.field_posted_at), formatTimestamp(snapshot.postedAt))
                DetailField(stringResource(R.string.field_payload_status), snapshot.payloadAvailability.name)
                DetailField(stringResource(R.string.field_title), snapshot.title)
                DetailField(stringResource(R.string.field_title_big), snapshot.titleBig)
                DetailField(stringResource(R.string.field_text), snapshot.text)
                DetailField(stringResource(R.string.field_big_text), snapshot.bigText)
                DetailField(stringResource(R.string.field_sub_text), snapshot.subText)
                DetailField(stringResource(R.string.field_info_text), snapshot.infoText)
                DetailField(stringResource(R.string.field_summary_text), snapshot.summaryText)
                DetailField(stringResource(R.string.field_conversation_title), snapshot.conversationTitle)
                DetailField(stringResource(R.string.field_ticker), snapshot.tickerText)
                DetailField(stringResource(R.string.field_template), snapshot.template)
                DetailField(stringResource(R.string.field_text_lines), snapshot.textLines.joinToString("\n"))
                DetailField(stringResource(R.string.field_remote_history), snapshot.remoteInputHistory.joinToString("\n"))
                DetailField(stringResource(R.string.field_structured_extras), snapshot.structuredExtrasJson)

                if (snapshotTextFields(snapshot).none(String::isNotBlank) && relation.messages.isEmpty() && relation.attachments.isEmpty()) {
                    Text(stringResource(R.string.no_text_in_payload), style = MaterialTheme.typography.bodyLarge)
                }

                if (relation.messages.isNotEmpty()) {
                    Text(stringResource(R.string.messages), style = MaterialTheme.typography.titleSmall)
                    relation.messages.forEach { message ->
                        DetailField(
                            label = stringResource(
                                R.string.message_header,
                                if (message.isHistoric) stringResource(R.string.historic) else stringResource(R.string.current_message),
                                message.sender,
                                formatTimestamp(message.timestamp),
                            ),
                            value = message.text,
                        )
                        DetailField(stringResource(R.string.message_mime), message.mimeType)
                        DetailField(stringResource(R.string.message_uri), message.dataUri)
                    }
                }

                if (relation.attachments.isNotEmpty()) {
                    Text(stringResource(R.string.attachments), style = MaterialTheme.typography.titleSmall)
                    relation.attachments.forEach { attachment ->
                        if (attachment.readStatus == AttachmentReadStatus.COPIED &&
                            attachment.mimeType.startsWith("image/") &&
                            attachment.localPath.isNotBlank()
                        ) {
                            AttachmentImagePreview(
                                attachment = attachment,
                                onOpenFullscreen = { fullscreenAttachmentId = attachment.attachmentId },
                            )
                        }
                        DetailField(stringResource(R.string.attachment_kind), attachment.kind.name)
                        DetailField(stringResource(R.string.attachment_source), attachment.sourceUri)
                        DetailField(stringResource(R.string.attachment_mime), attachment.mimeType)
                        DetailField(stringResource(R.string.attachment_status), attachment.readStatus.name)
                        DetailField(
                            stringResource(R.string.attachment_dimensions),
                            listOfNotNull(attachment.pixelWidth, attachment.pixelHeight)
                                .joinToString(" × ")
                                .takeUnless(String::isBlank)
                                .orEmpty(),
                        )
                        DetailField(stringResource(R.string.attachment_description), attachment.contentDescription)
                        DetailField(stringResource(R.string.attachment_hash), attachment.sha256)
                    }
                }
            }
        }
    }
    fullscreenAttachmentId?.let { attachmentId ->
        relation.attachments.firstOrNull { it.attachmentId == attachmentId }?.let { attachment ->
            FullscreenImageDialog(
                attachment = attachment,
                onDismiss = { fullscreenAttachmentId = null },
            )
        }
    }
}

private fun snapshotTextFields(snapshot: NotificationSnapshot): List<String> = listOf(
    snapshot.title,
    snapshot.titleBig,
    snapshot.text,
    snapshot.bigText,
    snapshot.subText,
    snapshot.infoText,
    snapshot.summaryText,
    snapshot.conversationTitle,
    snapshot.tickerText,
    snapshot.template,
    snapshot.structuredExtrasJson.takeUnless { it == "{}" }.orEmpty(),
    *snapshot.textLines.toTypedArray(),
    *snapshot.remoteInputHistory.toTypedArray(),
)
