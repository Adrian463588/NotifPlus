package com.notifplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.notifplus.R
import com.notifplus.domain.model.NotificationAttachment
import java.io.File

@Composable
fun FullscreenImageDialog(
    attachment: NotificationAttachment,
    onDismiss: () -> Unit,
) {
    var scale by rememberSaveable(attachment.localPath) { mutableStateOf(1f) }
    var offset by remember(attachment.localPath) { mutableStateOf(Offset.Zero) }
    var loadFailed by rememberSaveable(attachment.localPath) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(attachment.localPath) { 
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset += pan
                    } 
                },
        ) {
            if (loadFailed) {
                Text(
                    text = stringResource(R.string.attachment_preview_unavailable),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                AsyncImage(
                    model = File(attachment.localPath),
                    contentDescription = attachment.contentDescription.ifBlank {
                        stringResource(R.string.notification_image_content_description)
                    },
                    contentScale = ContentScale.Fit,
                    onError = { loadFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
            IconButton(
                onClick = onDismiss, 
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                )
            }
        }
    }
}
