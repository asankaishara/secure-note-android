package com.ishara.securenotes

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ishara.securenotes.ui.LockScreen
import com.ishara.securenotes.ui.NotesScreen
import com.ishara.securenotes.ui.NotesViewModel
import com.ishara.securenotes.ui.theme.SecureNotesTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val vm: NotesViewModel by viewModels()

    private var lockMessage by mutableStateOf<String?>(null)

    /** True while the fingerprint/PIN prompt is showing, so it doesn't count as "leaving the app". */
    private var authInProgress = false

    /** True while the system file picker (export/import) is open. */
    private var pickerOpen = false

    /** Show the unlock prompt automatically once each time the app is locked. */
    private var autoPromptPending = true

    private val createPdf =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            pickerOpen = false
            if (uri != null) vm.exportTo(uri) else vm.cancelExport()
        }

    private val openPdf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickerOpen = false
            if (uri != null) vm.onImportFilePicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the preview in the recent-apps screen.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        setContent {
            SecureNotesTheme {
                if (vm.unlocked) {
                    NotesScreen(
                        vm = vm,
                        onExportPasswordChosen = { password ->
                            vm.prepareExport(password)
                            pickerOpen = true
                            createPdf.launch("SecureNotes-${today()}.pdf")
                        },
                        onPickImportFile = {
                            pickerOpen = true
                            openPdf.launch(arrayOf("application/pdf"))
                        },
                        onLockNow = { lock(autoPrompt = false) },
                    )
                } else {
                    LockScreen(message = lockMessage, onUnlock = { authenticate() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!vm.unlocked && autoPromptPending && !authInProgress) {
            autoPromptPending = false
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock whenever the app goes to the background (home button, screen off, app switch).
        if (!isChangingConfigurations && !authInProgress && !pickerOpen) {
            lock(autoPrompt = true)
        }
    }

    private fun lock(autoPrompt: Boolean) {
        vm.unlocked = false
        lockMessage = null
        autoPromptPending = autoPrompt
    }

    private fun authenticate() {
        if (authInProgress) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            lockMessage = "Set up a screen lock (PIN, pattern, password or fingerprint) in your phone's " +
                "Settings to protect your notes, then tap Unlock."
            return
        }

        authInProgress = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    lockMessage = null
                    vm.unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    lockMessage = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        null
                    } else {
                        errString.toString()
                    }
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secure Notes")
            .setSubtitle("Use your fingerprint or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            authInProgress = false
            lockMessage = "Could not show the unlock prompt: ${e.message}"
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
