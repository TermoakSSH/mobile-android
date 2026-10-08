package com.termoak.app.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Unlocking with the fingerprint or face, or the screen lock (the iOS app's
 * `DeviceAuth`): before showing a private key, and for the app lock. A phone
 * without a screen lock has nothing to unlock with, so it goes on.
 */
class DeviceAuth(private val context: Context, private val legacy: (String, (Boolean) -> Unit) -> Unit) {
    /** The phone has a screen lock (PIN, pattern, password, maybe biometrics). */
    val secure: Boolean get() = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Asks the owner to unlock, saying why ([reason]); [onResult] gets whether they did. */
    fun unlock(reason: String, onResult: (Boolean) -> Unit) {
        if (!secure) return onResult(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) prompt(reason, onResult) else legacy(reason, onResult)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun prompt(reason: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(reason)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(), context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }
}

/** The unlock of this screen (before Android 11: the screen lock's own screen). */
@Composable
fun rememberDeviceAuth(): DeviceAuth {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        pending[0]?.invoke(r.resultCode == Activity.RESULT_OK)
        pending[0] = null
    }
    return remember(context) {
        DeviceAuth(context) { reason, onResult ->
            @Suppress("DEPRECATION")
            val intent = context.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(reason, null)
            if (intent == null) {
                onResult(true)
            } else {
                pending[0] = onResult
                launcher.launch(intent)
            }
        }
    }
}
