package io.github.pxldi.schall.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Android 17 (SDK 37) blocks an app that targets it from every address on
 * the local network until ACCESS_LOCAL_NETWORK is granted. A blocked connect
 * does not fail, it times out, and DNS still answers, so without this the
 * sign-in to a server on the same Wi-Fi looks like the server is down. */
fun localNetworkAccessMissing(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 37 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED

/** Asks once, when the gate first opens, so both a fresh sign-in and a stored
 * session on a LAN server are covered before their first request. */
@Composable
fun AskLocalNetworkAccess() {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (localNetworkAccessMissing(context)) ask.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}

/** What the sign-in screen says under the button when a connect timed out and
 * the permission is the likely reason. */
const val LOCAL_NETWORK_HINT = "Could not reach the server. Allow Schall to access the local network in Android's app settings, then try again."
