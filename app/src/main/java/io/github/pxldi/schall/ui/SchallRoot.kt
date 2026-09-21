@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.pxldi.schall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.pxldi.schall.SchallApp
import io.github.pxldi.schall.data.Api
import io.github.pxldi.schall.data.StoredSession
import io.github.pxldi.schall.ui.common.AskLocalNetworkAccess
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.LocalLive
import io.github.pxldi.schall.ui.downloads.DownloadsScreen
import io.github.pxldi.schall.ui.review.ReviewListScreen
import io.github.pxldi.schall.ui.review.ReviewQuestionScreen
import io.github.pxldi.schall.ui.review.reviewCount
import io.github.pxldi.schall.ui.search.SearchScreen
import io.github.pxldi.schall.ui.settings.SettingsScreen
import io.github.pxldi.schall.ui.signin.SignInScreen
import io.github.pxldi.schall.ui.wants.WantsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private sealed interface Gate {
    data object Unknown : Gate
    data object SignedOut : Gate
    data class SignedIn(val stored: StoredSession) : Gate
}

/** The sign-in gate. Nothing is drawn until the stored session has been read
 * once; then either the sign-in screen or the app behind it. */
@Composable
fun SchallRoot(app: SchallApp, reviewLinks: Flow<Unit>) {
    val gate by remember(app) {
        app.sessions.current.map { stored -> if (stored == null) Gate.SignedOut else Gate.SignedIn(stored) }
    }.collectAsStateWithLifecycle(initialValue = Gate.Unknown)

    when (val state = gate) {
        Gate.Unknown -> Box(Modifier.fillMaxSize())
        Gate.SignedOut -> {
            AskLocalNetworkAccess()
            SignInScreen(app)
        }
        is Gate.SignedIn -> {
            AskLocalNetworkAccess()
            SignedIn(app, state.stored, reviewLinks)
        }
    }
}

@Composable
private fun SignedIn(app: SchallApp, stored: StoredSession, reviewLinks: Flow<Unit>) {
    val api = remember(stored) { Api(stored.session, app.http) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The event stream is open while the app is in front and closed when it
    // is not; coming back to the front reopens it and every screen refreshes.
    LaunchedEffect(api) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { app.live.run(api.session) }
    }
    val storeOwner = LocalViewModelStoreOwner.current ?: error("no store owner")

    CompositionLocalProvider(LocalApi provides api, LocalLive provides app.live, LocalAppStore provides storeOwner) {
        val nav = rememberNavController()
        NavHost(nav, startDestination = "home") {
            composable("home") { Home(app, stored, reviewLinks, openQuestion = { nav.navigate("question/$it") }) }
            composable("question/{id}") { entry ->
                ReviewQuestionScreen(id = entry.arguments?.getString("id").orEmpty(), back = { nav.popBackStack() })
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Review("Review", Icons.Outlined.Inbox),
    Downloads("Downloads", Icons.Outlined.Download),
    Wants("Wants", Icons.AutoMirrored.Outlined.QueueMusic),
    Search("Search", Icons.Outlined.Search),
    Settings("Settings", Icons.Outlined.Settings),
}

@Composable
private fun Home(app: SchallApp, stored: StoredSession, reviewLinks: Flow<Unit>, openQuestion: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(reviewLinks) { reviewLinks.collect { tab = Tab.Review.ordinal } }
    val waiting = reviewCount()

    Scaffold(
        topBar = { TopAppBar(title = { Text(Tab.entries[tab].label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry.ordinal,
                        onClick = { tab = entry.ordinal },
                        label = { Text(entry.label) },
                        icon = {
                            if (entry == Tab.Review && waiting > 0) {
                                BadgedBox(badge = { Badge { Text(waiting.toString()) } }) { Icon(entry.icon, contentDescription = null) }
                            } else {
                                Icon(entry.icon, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (Tab.entries[tab]) {
                Tab.Review -> ReviewListScreen(openQuestion)
                Tab.Downloads -> DownloadsScreen(openQuestion)
                Tab.Wants -> WantsScreen()
                Tab.Search -> SearchScreen()
                Tab.Settings -> SettingsScreen(app, stored)
            }
        }
    }
}
