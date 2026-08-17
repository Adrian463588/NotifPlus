package com.notifplus.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notifplus.R
import com.notifplus.presentation.QuickFilterType

data class QuickFilterItem(
    val type: QuickFilterType,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun FilterChipGroup(
    selectedFilter: QuickFilterType,
    onFilterSelected: (QuickFilterType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        QuickFilterItem(
            type = QuickFilterType.ALL,
            label = "Semua",
            icon = Icons.Default.Notifications,
        ),
        QuickFilterItem(
            type = QuickFilterType.UNREAD,
            label = "Belum Dibaca",
            icon = Icons.Default.MarkEmailUnread,
        ),
        QuickFilterItem(
            type = QuickFilterType.FAVORITES,
            label = "Favorit",
            icon = Icons.Default.Star,
        ),
        QuickFilterItem(
            type = QuickFilterType.WITH_MEDIA,
            label = "Ada Media",
            icon = Icons.Default.Image,
        ),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { item ->
            val isSelected = selectedFilter == item.type
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(item.type) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Done else item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
