package com.example.policeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.policeapp.theme.PoliceAppTheme

class MainActivity : ComponentActivity() {
  private var pendingSosDetailId by mutableStateOf<String?>(null)

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { /* The app can still run; notifications require this permission on Android 13+. */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingSosDetailId = intent.extractSosDetailId()

    enableEdgeToEdge()
    requestNotificationPermissionIfNeeded()
    setContent {
      PoliceAppTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            pendingSosDetailId = pendingSosDetailId,
            onSosDetailConsumed = { pendingSosDetailId = null }
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingSosDetailId = intent.extractSosDetailId()
  }

  override fun onResume() {
    super.onResume()
    requestNotificationPermissionIfNeeded()
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun Intent?.extractSosDetailId(): String? {
    if (this?.getBooleanExtra(EXTRA_NAVIGATE_TO_SOS, false) != true) return null
    return getStringExtra(EXTRA_SOS_ID)?.takeIf { it.isNotBlank() }
  }

  companion object {
    private const val EXTRA_NAVIGATE_TO_SOS = "navigate_to_sos"
    private const val EXTRA_SOS_ID = "sos_id"
  }
}
