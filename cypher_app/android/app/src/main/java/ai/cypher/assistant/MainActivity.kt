package ai.cypher.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "cypher_prefs"
        private const val KEY_ALWAYS_ON = "alwaysOnCypherEnabled"
    }

    private val corePermissions = listOfNotNull(
        Manifest.permission.RECORD_AUDIO,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
    )

    private val optionalPermissionLabels = mapOf(
        Manifest.permission.READ_PHONE_STATE to "Phone State",
        Manifest.permission.CALL_PHONE to "Phone Calls",
        Manifest.permission.SEND_SMS to "SMS",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.ACCESS_FINE_LOCATION to "Location",
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.ANSWER_PHONE_CALLS to "Answer Calls",
    )

    private var serviceStarted = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val updateManager by lazy { UpdateManager(this) }

    private val corePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allCoreGranted = corePermissions.all {
            result[it] == true || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allCoreGranted) {
            setAlwaysOnEnabled(true)
            startCypherService()
        } else {
            Toast.makeText(this, "Cypher needs microphone access to function.", Toast.LENGTH_LONG).show()
        }
    }

    private val optionalPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filter { it.value == false }.keys
        if (denied.isNotEmpty()) {
            val deniedLabels = denied.mapNotNull { perm -> optionalPermissionLabels[perm] }
            Toast.makeText(this, "Optional permissions denied: $deniedLabels", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Optional permissions granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    CypherUI(
                        onStart = { requestCorePermissions() },
                        onGrantOptional = { requestOptionalPermissions() },
                        onCheckUpdate = { checkForUpdate() },
                        hasCore = hasCorePermissions(),
                    )
                }
            }
        })

        if (hasCorePermissions()) {
            setAlwaysOnEnabled(true)
            startCypherService()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!serviceStarted && hasCorePermissions()) {
            startCypherService()
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun hasCorePermissions(): Boolean {
        return corePermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestCorePermissions() {
        val needed = corePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isNotEmpty()) {
            corePermissionLauncher.launch(needed)
        } else {
            setAlwaysOnEnabled(true)
            startCypherService()
        }
    }

    private fun requestOptionalPermissions() {
        val optionalNeeded = optionalPermissionLabels.keys.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (optionalNeeded.isNotEmpty()) {
            optionalPermissionLauncher.launch(optionalNeeded)
        } else {
            Toast.makeText(this, "All optional permissions already granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCypherService() {
        if (serviceStarted) return
        serviceStarted = true
        try {
            val intent = Intent(this, CypherBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.i(TAG, "Cypher service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
            Toast.makeText(this, "Failed to start Cypher: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setAlwaysOnEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALWAYS_ON, enabled)
            .apply()
    }

    private fun checkForUpdate() {
        activityScope.launch {
            Toast.makeText(this@MainActivity, "Checking for updates...", Toast.LENGTH_SHORT).show()
            try {
                val release = withContext(Dispatchers.IO) {
                    updateManager.checkForUpdate()
                }
                if (release == null) {
                    Toast.makeText(this@MainActivity, "Could not check for updates. Check connection.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val current = updateManager.getCurrentVersion()
                if (release.version == current) {
                    Toast.makeText(this@MainActivity, "Already up to date ($current).", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Toast.makeText(this@MainActivity, "Update available: ${release.version} (current: $current)", Toast.LENGTH_LONG).show()
                val apk = withContext(Dispatchers.IO) {
                    updateManager.downloadUpdate(release.apkUrl)
                }
                if (apk == null) {
                    Toast.makeText(this@MainActivity, "Download failed.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val ok = updateManager.installApk(apk)
                if (ok) {
                    Toast.makeText(this@MainActivity, "Downloaded ${release.version}. Follow prompts to install.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Installation failed.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                Toast.makeText(this@MainActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun CypherUI(
    onStart: () -> Unit,
    onGrantOptional: () -> Unit,
    onCheckUpdate: () -> Unit,
    hasCore: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("CYPHER", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            Spacer(Modifier.height(8.dp))
            Text("An AI agent by Anay", fontSize = 14.sp, color = Color(0xFF81A1C1))
            Spacer(Modifier.height(32.dp))
            Text(
                if (hasCore) "Cypher is running in the background."
                else "First Boot — Permission Setup",
                fontSize = 18.sp, color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (hasCore) "Say \"Zed 18\" to activate."
                else "Cypher needs microphone access to function.\nOther permissions (contacts, SMS, etc.) are optional.",
                fontSize = 14.sp, color = Color(0xFF81A1C1), lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0D1117)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    if (hasCore) "Restart Cypher"
                    else "Grant Core Permissions",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
            if (hasCore) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onGrantOptional,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00E5FF)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Grant Optional Permissions", fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCheckUpdate,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF81A1C1)
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Check for Updates", fontSize = 14.sp)
                }
            }
        }
    }
}
