package com.notifplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.notifplus.presentation.AccessViewModel
import com.notifplus.ui.NotifPlusApp
import com.notifplus.ui.theme.NotifPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val accessViewModel: AccessViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accessViewModel.cleanupExpiredArchive()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                accessViewModel.refresh()
            }
        }
        setContent {
            NotifPlusTheme {
                NotifPlusApp(accessViewModel = accessViewModel)
            }
        }
    }
}
