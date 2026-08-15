package com.notifplus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.notifplus.R
import com.notifplus.domain.model.NotificationThreadSummary
import com.notifplus.domain.model.RemovalOrigin
import java.text.DateFormat
import java.util.Date

fun formatTimestamp(timestamp: Long): String = DateFormat.getDateTimeInstance().format(Date(timestamp))

@Composable
fun removalLabel(summary: NotificationThreadSummary): String = when (summary.removalOrigin) {
    RemovalOrigin.USER -> stringResource(R.string.removed_by_user)
    RemovalOrigin.SOURCE_APP -> stringResource(R.string.removed_by_source_app)
    RemovalOrigin.NOTIFPLUS -> stringResource(R.string.removed_by_notifplus)
    RemovalOrigin.SYSTEM -> stringResource(R.string.removed_by_system)
    RemovalOrigin.UNKNOWN -> stringResource(R.string.removed_unknown)
}
