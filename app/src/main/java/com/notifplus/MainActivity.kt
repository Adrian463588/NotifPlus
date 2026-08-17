package com.notifplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.notifplus.domain.repository.SecurityRepository
import com.notifplus.presentation.AccessViewModel
import com.notifplus.security.BiometricAuthManager
import com.notifplus.ui.NotifPlusApp
import com.notifplus.ui.lock.LockScreen
import com.notifplus.ui.theme.NotifPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val accessViewModel: AccessViewModel by viewModels()

    @Inject
    lateinit var securityRepository: SecurityRepository

    private var isUnlocked by mutableStateOf(true)
    private var lockErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        accessViewModel.cleanupExpiredArchive()

        lifecycleScope.launch {
            val isLockEnabled = securityRepository.observeBiometricLockEnabled().first()
            if (isLockEnabled) {
                isUnlocked = false
                requestBiometricUnlock()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                accessViewModel.refresh()
            }
        }

        setContent {
            NotifPlusTheme {
                if (isUnlocked) {
                    NotifPlusApp(accessViewModel = accessViewModel)
                } else {
                    LockScreen(
                        onUnlockClicked = ::requestBiometricUnlock,
                        errorMessage = lockErrorMessage,
                    )
                }
            }
        }
    }

    private fun requestBiometricUnlock() {
        if (!BiometricAuthManager.canAuthenticate(this)) {
            // Biometrics not enrolled or supported on device, bypass lock
            isUnlocked = true
            return
        }

        lockErrorMessage = null
        BiometricAuthManager.authenticate(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            onSuccess = {
                isUnlocked = true
                lockErrorMessage = null
            },
            onError = { error ->
                lockErrorMessage = error
            },
        )
    }
}

