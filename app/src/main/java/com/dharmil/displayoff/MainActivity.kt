package com.dharmil.displayoff

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF101419)
private val CardInk = Color(0xFF1B2229)
private val Mint = Color(0xFF80CBC4)
private val OnMint = Color(0xFF003733)
private val Paper = Color(0xFFF2F5F6)
private val Muted = Color(0xFFBBC6CB)
private val Warning = Color(0xFFF0B86E)
private val Error = Color(0xFFFFB4AB)
private val Border = Color(0xFF52616A)

class MainActivity : ComponentActivity() {
    private var resumeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Mint,
                    onPrimary = OnMint,
                    background = Ink,
                    onBackground = Paper,
                    surface = CardInk,
                    onSurface = Paper,
                    secondary = Warning,
                    error = Error,
                    outline = Border,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    DisplayOffScreen(
                        controller = remember { LocalAdbController(applicationContext) },
                        openDeveloperOptions = ::openDeveloperOptions,
                        resumeSignal = resumeSignal,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeSignal += 1
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

@Composable
private fun DisplayOffScreen(
    controller: LocalAdbController,
    openDeveloperOptions: () -> Unit,
    resumeSignal: Int,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences("display-off-state", android.content.Context.MODE_PRIVATE)
    }

    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable {
        mutableStateOf(preferences.getString("connectPort", "") ?: "")
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var paired by rememberSaveable { mutableStateOf(preferences.getBoolean("paired", false)) }
    var showPairingForm by rememberSaveable { mutableStateOf(!paired) }
    var showAdvanced by rememberSaveable { mutableStateOf(!paired) }
    var discovering by rememberSaveable { mutableStateOf(false) }
    var connected by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable {
        mutableStateOf(
            if (controller.hasLocalKey()) "Pairing key found on this phone. Connect to Wireless debugging to continue."
            else "Set up Wireless debugging, then pair this app once.",
        )
    }

    suspend fun refreshPorts() {
        if (busy || discovering) return
        discovering = true
        connected = false
        status = "Finding this phone’s Wireless debugging ports…"
        val result = runCatching { controller.discoverWirelessDebugPorts() }
        result.onSuccess { ports ->
            ports.pairingPort?.let { if (!paired) pairingPort = it.toString() }
            ports.connectPort?.let {
                connectPort = it.toString()
                preferences.edit().putString("connectPort", it.toString()).apply()
            }
            status = when {
                ports.connectPort != null && !paired && ports.pairingPort != null ->
                    "Both ports found. Enter the six-digit code shown in Android Settings to pair once."
                ports.connectPort != null && paired ->
                    "Wireless debugging is ready. The current port was filled automatically."
                ports.connectPort != null ->
                    "Wireless debugging port found. Pair this app once to enable Display off."
                ports.pairingPort != null ->
                    "Pairing port found. Enter the six-digit code shown in Android Settings."
                else ->
                    "No local ADB port found. Turn on Wireless debugging and connect to Wi-Fi, then tap Find ports."
            }
        }.onFailure {
            status = "Port discovery failed. You can still enter the port manually."
        }
        discovering = false
    }

    fun sendDisplayOff() {
        val port = connectPort.toIntOrNull()
        if (!paired) {
            status = "Pair this app once in Pairing & troubleshooting before turning the display off."
        } else if (port !in 1..65535 || busy || discovering) {
            status = "Wireless debugging port is not ready. Tap Find ports or open Developer options."
        } else {
            busy = true
            connected = false
            status = "Sending the display-off command…"
            scope.launch {
                val outcome = runCatching {
                    withContext(Dispatchers.IO) { controller.turnDisplayOff(port!!) }
                }
                outcome.onSuccess {
                    connected = true
                    preferences.edit().putString("connectPort", port.toString()).apply()
                    status = "Command sent. The display should be off; the phone stays unlocked."
                }.onFailure {
                    status = "Could not turn the display off: ${it.message?.take(140) ?: "check Wireless debugging and Find ports."}"
                }
                busy = false
            }
        }
    }

    LaunchedEffect(resumeSignal) {
        refreshPorts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                Image(
                    painter = painterResource(R.drawable.himalayan_logo),
                    contentDescription = "Himalayan 450 motorcycle",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(78.dp)
                        .height(72.dp)
                        .padding(4.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LOCAL ADB  /  NO SHIZUKU", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Display Off", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            "Turn off the panel · Keep the phone unlocked",
            color = Muted,
            fontSize = 14.sp,
        )

        Card(colors = CardDefaults.cardColors(containerColor = CardInk), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    when {
                        busy -> "WORKING"
                        discovering -> "FINDING PORTS"
                        connected -> "ADB READY"
                        paired -> "PAIRED"
                        else -> "STATUS"
                    },
                    color = Mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(status, color = Paper, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = openDeveloperOptions,
                colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Paper),
                modifier = Modifier.weight(1.2f).height(48.dp),
            ) { Text("Open Developer options", fontSize = 12.sp) }
            Button(
                onClick = { scope.launch { refreshPorts() } },
                enabled = !busy && !discovering,
                colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Paper),
                modifier = Modifier.weight(0.8f).height(48.dp),
            ) { Text(if (discovering) "Finding…" else "Find ports", fontSize = 13.sp) }
        }

        Button(
            onClick = ::sendDisplayOff,
            enabled = paired && connectPort.toIntOrNull() in 1..65535 && !busy && !discovering,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (busy) "Working…" else "Display off", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        TextButton(
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showAdvanced) "Hide pairing & troubleshooting" else "Pairing & troubleshooting")
        }

        if (showAdvanced) {
            Text("1  ·  Pair once", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (paired && !showPairingForm) {
                    "This app is paired. You do not need the pairing code again unless you revoke the pairing."
                } else {
                    "Turn on Wireless debugging, then choose “Pair device with pairing code.” The app finds the pairing port; enter the six-digit code shown by Android. Keep that code screen open until pairing finishes."
                },
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (paired && !showPairingForm) {
                Card(colors = CardDefaults.cardColors(containerColor = CardInk), shape = RoundedCornerShape(16.dp)) {
                    Text("Paired ✓  No code needed for normal use.", color = Paper, modifier = Modifier.padding(16.dp))
                }
                TextButton(onClick = { showPairingForm = true }, enabled = !busy) {
                    Text("Pair again", color = Warning)
                }
            } else {
                OutlinedTextField(
                    value = pairingPort,
                    onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                    label = { Text("Pairing port (auto-found when available)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-digit pairing code") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { scope.launch { refreshPorts() } },
                        enabled = !busy && !discovering,
                        colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Paper),
                        modifier = Modifier.weight(1f),
                    ) { Text("Find ports") }
                    Button(
                        onClick = {
                            val port = pairingPort.toIntOrNull()
                            val code = pairingCode
                            if (port !in 1..65535 || code.length != 6 || busy) {
                                status = "Wait for the pairing port to be found, then enter the six-digit code from Settings."
                            } else {
                                busy = true
                                connected = false
                                status = "Pairing this app…"
                                scope.launch {
                                    val outcome = runCatching {
                                        withContext(Dispatchers.IO) { controller.pair(port!!, code) }
                                    }
                                    pairingCode = ""
                                    outcome.onSuccess {
                                        paired = true
                                        showPairingForm = false
                                        showAdvanced = false
                                        preferences.edit().putBoolean("paired", true).apply()
                                        status = "Paired. The code will not be needed again unless you revoke the pairing."
                                    }.onFailure {
                                        status = "Pairing failed: ${it.message?.take(180) ?: "check the code and try again."}"
                                    }
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && !discovering,
                        modifier = Modifier.weight(1.2f),
                    ) { Text("Pair this app") }
                }
            }
    
            Text("2  ·  Manual connection", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "The current port is detected automatically when you open or return to this app. If discovery fails, refresh it or enter the port shown beside the phone’s IP address in Wireless debugging.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            OutlinedTextField(
                value = connectPort,
                onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Wireless debugging port (auto-found)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { scope.launch { refreshPorts() } },
                    enabled = !busy && !discovering,
                    modifier = Modifier.weight(0.9f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Paper),
                ) { Text("Find") }
    
                Button(
                    onClick = {
                        val port = connectPort.toIntOrNull()
                        if (!paired) {
                            status = "Pair this app first using Android’s 6-digit Wireless debugging pairing code."
                        } else if (port !in 1..65535 || busy) {
                            status = "Enter the current Wireless debugging port from Android Settings."
                        } else {
                            busy = true
                            connected = false
                            status = "Checking the local ADB connection…"
                            scope.launch {
                                val outcome = runCatching {
                                    withContext(Dispatchers.IO) { controller.connect(port!!) }
                                }
                                outcome.onSuccess {
                                    connected = true
                                    preferences.edit().putString("connectPort", port.toString()).apply()
                                    status = "Connected to this phone’s local ADB service. The screen-off button is ready."
                                }.onFailure {
                                    status = "Connection failed: ${it.message?.take(180) ?: "check the current port and Wireless debugging state."}"
                                }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(0.9f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardInk, contentColor = Paper),
                ) { Text("Connect") }
    
            }
    
            Spacer(Modifier.height(2.dp))
            Text(
                "Security: this app only connects to 127.0.0.1 and only sends “cmd display power-off 0”. Its ADB key stays in private app storage and is excluded from backups. You can revoke this pairing in Developer options → Wireless debugging.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            TextButton(
                onClick = {
                    controller.forgetLocalKey()
                    preferences.edit().remove("paired").apply()
                    paired = false
                    showPairingForm = true
                    connected = false
                    pairingCode = ""
                    pairingPort = ""
                    status = "This app’s local key was deleted. Also forget/revoke the app pairing in Wireless debugging to revoke Android’s authorization."
                },
                enabled = !busy,
                modifier = Modifier.align(Alignment.Start),
            ) { Text("Forget this app’s local key", color = Warning) }
        }
    }
}
