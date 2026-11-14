package com.example.cyberpunk.data

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.example.cyberpunk.R

object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val manager = context.getSystemService(BiometricManager::class.java)
                manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = context.getSystemService(BiometricManager::class.java)
                @Suppress("DEPRECATION")
                manager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit = {}
    ) {
        if (!isBiometricAvailable(context)) {
            onError(null)
            return
        }
        try {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(context.getString(R.string.biometric_prompt_title))
                .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                .setNegativeButton(
                    context.getString(R.string.biometric_use_pin),
                    executor
                ) { _, _ -> }
                .build()

            val cancellationSignal = CancellationSignal()
            prompt.authenticate(
                cancellationSignal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString?.toString())
                    }
                }
            )
        } catch (t: Throwable) {
            onError(t.localizedMessage)
        }
    }
}
