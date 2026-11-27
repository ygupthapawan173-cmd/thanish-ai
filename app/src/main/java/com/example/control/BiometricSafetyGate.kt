package com.example.control

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricSafetyGate {
    fun authenticate(
        context: Context,
        title: String = "Confirm Sensitive Action",
        subtitle: String = "Verify fingerprint or face to proceed",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onSuccess()
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        try {
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton("Cancel", executor) { _, _ -> onError("Cancelled by user.") }
                .build()

            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        onError(errString?.toString() ?: "Biometric verification failed.")
                    }
                }
            )
        } catch (e: Exception) {
            onError(e.message ?: "Biometric hardware unavailable.")
        }
    }
}
