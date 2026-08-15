package com.notifplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.notifplus.R
import com.notifplus.domain.model.NotificationAttachment
import java.io.File

@Composable
fun AttachmentImagePreview(
    attachment: NotificationAttachment,
    onOpenFullscreen: () -> Unit,
) {
    var loadFailed by rememberSaveable(attachment.localPath) { mutableStateOf(false) }
    if (loadFailed) {
        Text(
            text = stringResource(R.string.attachment_preview_unavailable),
            color = MaterialTheme.colorScheme.error
        )
        return
    }
    
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(File(attachment.localPath))
            .crossfade(true)
            .build(),
        contentDescription = attachment.contentDescription.ifBlank {
            stringResource(R.string.notification_image_content_description)
        },
        contentScale = ContentScale.Fit,
        onError = { loadFailed = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenFullscreen),
    )
}
