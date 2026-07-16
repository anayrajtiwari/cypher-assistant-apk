package ai.cypher.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requiredPermissions = listOfNotNull(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
        Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
    )

    private var serviceStarted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAllPermissions()) {
            startCypherService()
        } else {
            Toast.makeText(this, "Cypher needs all permissions.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    FirstBootScreen(onContinue = { requestAllPermissions() })
                }
            }
        })

        if (hasAllPermissions()) {
            startCypherService()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        val needed = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        } else {
            startCypherService()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun FirstBootScreen(onContinue: () -> Unit) {
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
            Text("First Boot — Permission Setup", fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Cypher needs microphone, phone, SMS,\ncontacts, location, and camera access.",
                fontSize = 14.sp, color = Color(0xFF81A1C1), lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0D1117)
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Grant Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
