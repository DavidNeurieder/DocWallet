package com.docwallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.docwallet.ui.navigation.DocWalletNavGraph
import com.docwallet.ui.navigation.Routes

class MainActivity : ComponentActivity() {
    private val pendingShareUris = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        handleShareIntent(intent)

        val app = application as DocWalletApplication

        setContent {
            val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    var isReady by remember { mutableStateOf(false) }
                    var startDestination by remember { mutableStateOf(Routes.LIBRARY) }

                    LaunchedEffect(Unit) {
                        if (app.encryptionManager.isFirstLaunch()) {
                            startDestination = Routes.PASSWORD_SETUP
                        } else if (app.encryptionManager.isPasswordSet()) {
                            startDestination = Routes.UNLOCK
                        } else {
                            app.encryptionManager.getMasterKeyForSession()
                            startDestination = Routes.LIBRARY
                        }
                        isReady = true
                    }

                    if (isReady) {
                        DocWalletNavGraph(
                            navController = navController,
                            startDestination = startDestination,
                            onUnlocked = { /* handled in NavGraph */ },
                            pendingImportUris = pendingShareUris.value,
                            onPendingImportConsumed = { pendingShareUris.value = emptyList() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    if (uri != null && uri.scheme == "content") {
                        pendingShareUris.value = listOf(uri)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null && uri.scheme == "content") {
                        pendingShareUris.value = listOf(uri)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        ?: emptyList()
                }
                pendingShareUris.value = uris.filter { it.scheme == "content" }
            }
        }
    }
}
