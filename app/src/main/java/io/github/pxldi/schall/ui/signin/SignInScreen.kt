package io.github.pxldi.schall.ui.signin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.pxldi.schall.SchallApp
import io.github.pxldi.schall.data.Api
import io.github.pxldi.schall.data.Session
import io.github.pxldi.schall.data.StoredSession
import io.github.pxldi.schall.data.json
import io.github.pxldi.schall.data.normaliseServer
import io.github.pxldi.schall.data.text
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class PhoneCode(val server: String, val token: String)

/** Server and token, typed or scanned. The QR code under Settings → Phone on
 * the web is `{"server":"…","token":"…"}`; the first code that parses fills
 * both fields. Sign in calls /me: it proves the token works and gives back
 * the name the token was minted under, which Settings shows. */
@Composable
fun SignInScreen(app: SchallApp) {
    var server by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val code = runCatching { json.decodeFromString<PhoneCode>(text) }.getOrNull()
        if (code == null) {
            error = "That code is not a Schall phone code."
        } else {
            server = code.server
            token = code.token
            error = ""
        }
    }

    fun submit() {
        val address = normaliseServer(server)
        val secret = token.trim()
        if (address.isEmpty()) {
            error = "Type the server address."
            return
        }
        if (secret.isEmpty()) {
            error = "Paste the token."
            return
        }
        scope.launch {
            busy = true
            error = ""
            try {
                val me = Api(Session(address, secret), app.http).me()
                app.sessions.save(StoredSession(address, secret, me.actor))
            } catch (failed: Exception) {
                error = failed.text()
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Schall", style = MaterialTheme.typography.displaySmall)
        Text(
            "Open Settings → Phone on the web, add a phone, and scan the code it shows. Or type both in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = {
            scan.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Point at the code under Settings → Phone.")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        }) { Text("Scan code") }
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Server") },
            placeholder = { Text("https://schall.example") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            placeholder = { Text("schall_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = ::submit, enabled = !busy && server.isNotBlank() && token.isNotBlank()) {
            Text(if (busy) "Signing in…" else "Sign in")
        }
    }
}
