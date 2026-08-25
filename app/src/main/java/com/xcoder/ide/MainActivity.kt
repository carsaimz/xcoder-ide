package com.xcoder.ide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.xcoder.ide.navigation.MainNavigation
import com.xcoder.ide.theme.XCoderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry point for XCoder IDE.
 *
 * Patterned after AndroidIDE's [BaseEditorActivity] (809 lines) which handles:
 * - Edge-to-edge display via [enableEdgeToEdge]
 * - Incoming intent processing (ACTION_VIEW / ACTION_EDIT / ACTION_OPEN_DOCUMENT)
 * - Permission requests on startup (storage, notifications)
 * - Splash screen via AndroidX SplashScreen API
 * - Content view delegation to Compose [MainNavigation]
 *
 * Also borrows from Sketchware-IA's main activity pattern for
 * project-scoped state restoration and the drawer-based navigation model.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Pending file URI received via intent before Compose is ready. */
    private var pendingFileUri: Uri? by mutableStateOf(null)

    /** Tracks whether the splash screen should keep showing. */
    private var keepSplashOnScreen = true

    /**
     * Optional: injected preference repo for reading the initial navigation
     * target and theme preference before Compose is set up.
     */
    @Suppress("unused")
    @Inject
    lateinit var preferenceStore: com.xcoder.core.settings.PreferencesManager

    // -----------------------------------------------------------------------
    //  Permission launcher
    // -----------------------------------------------------------------------

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Storage permission is required for XCoder IDE to function.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the AndroidX SplashScreen BEFORE super.onCreate so that
        // the splash-screen theme is kept while we initialize.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Process the incoming intent that launched this activity.
        handleIncomingIntent(intent)

        // Request storage permissions (Android 10 and below).
        requestStoragePermissionsIfNeeded()

        // Pre-load any heavy resources on a background thread.
        // Once done, dismiss the splash screen.
        preloadIdeResources {
            keepSplashOnScreen = false
        }

        setContent {
            XCoderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        initialFileUri = pendingFileUri,
                        onFileHandled = {
                            pendingFileUri = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Sync edge-to-edge insets after every resume in case the system
        // changed the navigation-bar mode (gesture vs 3-button).
        val contentView = findViewById<View>(android.R.id.content)
        contentView?.viewTreeObserver?.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    syncSystemBars()
                }
            }
        )
    }

    // -----------------------------------------------------------------------
    //  Intent handling
    // -----------------------------------------------------------------------

    /**
     * Handles incoming intents to open files from external apps.
     *
     * Supported actions:
     * - [Intent.ACTION_VIEW] – open a file for viewing
     * - [Intent.ACTION_EDIT] – open a file for editing
     * - [Intent.ACTION_OPEN_DOCUMENT] – pick a file via SAF
     *
     * Pattern from AndroidIDE's `BaseEditorActivity#handleIntent()`.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW,
            Intent.ACTION_EDIT -> {
                val uri: Uri? = intent.data
                if (uri != null) {
                    // Take persistable URI permission so we can re-open later.
                    takePersistableUriPermission(uri, intent.flags)
                    pendingFileUri = uri
                    showToast("Opening: ${uri.lastPathSegment ?: uri.toString()}")
                }
            }
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_GET_CONTENT -> {
                val uri: Uri? = intent.data
                if (uri != null) {
                    takePersistableUriPermission(uri, intent.flags)
                    pendingFileUri = uri
                }
            }
        }
    }

    /**
     * Takes persistable URI permissions when available.
     * Follows AndroidIDE's permission model.
     */
    private fun takePersistableUriPermission(uri: Uri, flags: Int) {
        val takeFlags = (flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        ))
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            // Not a persistable grant — ignore.
        }
    }

    // -----------------------------------------------------------------------
    //  Permissions
    // -----------------------------------------------------------------------

    /**
     * On Android 10 (API 29) and below, request READ/WRITE_EXTERNAL_STORAGE.
     * Android 11+ uses scoped storage, so we request MANAGE_EXTERNAL_STORAGE
     * via the system settings intent instead.
     *
     * Pattern from Termux's `TermuxActivity#requestStoragePermission()`.
     */
    private fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: check MANAGE_EXTERNAL_STORAGE.
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    // Fallback: open general app settings.
                    val fallback = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(fallback)
                }
            }
        } else {
            // Android 10 and below: request legacy storage permissions.
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (needed.isNotEmpty()) {
                storagePermissionLauncher.launch(needed.toTypedArray())
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Preloading
    // -----------------------------------------------------------------------

    /**
     * Simulates the loading of heavy IDE resources (editor, LSP, terminal, etc.)
     * on a background thread. Once done, dismisses the splash screen.
     *
     * In production, this would initialize:
     * - sora-editor language registry
     * - LSP client connections
     * - Terminal session bootstrap (JNI)
     * - Plugin system classloader
     *
     * Pattern from AndroidIDE's splash-to-editor loading sequence.
     */
    private fun preloadIdeResources(onReady: () -> Unit) {
        Thread {
            try {
                // Simulate initialization delay.
                // Real code: LanguageRegistry.init(); LspManager.connect(); TerminalJni.load()
                Thread.sleep(800)
            } catch (_: InterruptedException) {
                // Ignored.
            } finally {
                runOnUiThread(onReady)
            }
        }.start()
    }

    // -----------------------------------------------------------------------
    //  System bars
    // -----------------------------------------------------------------------

    /**
     * Synchronizes the system bars (status bar + nav bar) with the
     * current theme. Called from [onResume] and after edge-to-edge setup.
     *
     * Pattern from AndroidIDE's `BaseEditorActivity#setupWindowDecor()`.
     */
    private fun syncSystemBars() {
        val window = this.window
        // The navigation bar should be translucent so content draws behind it.
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Status bar is already handled by edge-to-edge + the Compose theme.
    }

    // -----------------------------------------------------------------------
    //  Utility
    // -----------------------------------------------------------------------

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
