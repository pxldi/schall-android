package io.github.pxldi.schall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.pxldi.schall.ui.common.LocalPage
import io.github.pxldi.schall.ui.common.PageState
import io.github.pxldi.schall.ui.overview.OverviewScreen
import io.github.pxldi.schall.ui.theme.Duoton
import io.github.pxldi.schall.ui.theme.LocalInks
import io.github.pxldi.schall.ui.theme.Misprint
import io.github.pxldi.schall.ui.theme.MisprintIcon
import io.github.pxldi.schall.ui.theme.ProvideInks
import io.github.pxldi.schall.ui.theme.Type
import androidx.compose.foundation.layout.Box
import io.github.pxldi.schall.SchallApp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.pxldi.schall.data.Api
import io.github.pxldi.schall.data.StoredSession
import io.github.pxldi.schall.ui.common.LocalApi
import io.github.pxldi.schall.ui.common.LocalAppStore
import io.github.pxldi.schall.ui.common.LocalLive
import io.github.pxldi.schall.ui.downloads.DownloadsScreen
import io.github.pxldi.schall.ui.artist.ArtistScreen
import io.github.pxldi.schall.ui.release.ReleaseScreen
import io.github.pxldi.schall.ui.review.ReviewListScreen
import io.github.pxldi.schall.ui.review.ReviewQuestionScreen
import io.github.pxldi.schall.ui.review.reviewCount
import io.github.pxldi.schall.ui.search.SearchScreen
import io.github.pxldi.schall.ui.settings.SettingsScreen
import io.github.pxldi.schall.ui.signin.SignInScreen
import io.github.pxldi.schall.ui.wants.WantsScreen
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
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
        Gate.SignedOut -> SignInScreen(app)
        is Gate.SignedIn -> SignedIn(app, state.stored, reviewLinks)
    }
}

/** The app behind the gate. `http` is the app's client; screenshot tests pass
 * one that answers from fixtures. */
@Composable
internal fun SignedIn(app: SchallApp, stored: StoredSession, reviewLinks: Flow<Unit>, http: OkHttpClient = app.http) {
    val api = remember(stored, http) { Api(stored.session, http) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The event stream is open while the app is in front and closed when it
    // is not; coming back to the front reopens it and every screen refreshes.
    LaunchedEffect(api) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { app.live.run(api.session) }
    }
    val storeOwner = LocalViewModelStoreOwner.current ?: error("no store owner")
    val page = remember { PageState() }

    CompositionLocalProvider(
        LocalApi provides api,
        LocalLive provides app.live,
        LocalAppStore provides storeOwner,
        LocalPage provides page,
    ) {
        ProvideInks(page.inks) {
            val nav = rememberNavController()
            val back: () -> Unit = { nav.popBackStack() }
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize().background(Duoton.ground)) {
                composable("home") {
                    Home(
                        reviewLinks = reviewLinks,
                        openQuestion = { nav.navigate("question/$it") },
                        openSettings = { nav.navigate("settings") },
                        openWants = { nav.navigate("wants") },
                        openRelease = { nav.navigate("release/$it") },
                        openArtist = { nav.navigate("artist/$it") },
                    )
                }
                composable("question/{id}") { entry ->
                    ReviewQuestionScreen(
                        id = entry.arguments?.getString("id").orEmpty(),
                        back = back,
                        openInstead = { next -> nav.navigate("question/$next") { popUpTo("question/{id}") { inclusive = true } } },
                    )
                }
                composable("settings") { SettingsScreen(app, stored, back) }
                composable("wants") { WantsScreen(back) }
                composable("artist/{id}") { entry ->
                    ArtistScreen(entry.arguments?.getString("id").orEmpty(), back, openRelease = { nav.navigate("release/$it") })
                }
                composable("release/{id}") { entry ->
                    ReleaseScreen(entry.arguments?.getString("id").orEmpty(), back, openArtist = { nav.navigate("artist/$it") })
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: Misprint) {
    Overview("Overview", Misprint.Listen),
    Review("Review", Misprint.Checklist),
    Downloads("Downloads", Misprint.Download),
    Search("Search", Misprint.Search),
}

@Composable
private fun Home(
    reviewLinks: Flow<Unit>,
    openQuestion: (String) -> Unit,
    openSettings: () -> Unit,
    openWants: () -> Unit,
    openRelease: (String) -> Unit,
    openArtist: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(reviewLinks) { reviewLinks.collect { tab = Tab.Review.ordinal } }
    val waiting = reviewCount()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.Overview -> OverviewScreen(
                    openReview = { tab = Tab.Review.ordinal },
                    openDownloads = { tab = Tab.Downloads.ordinal },
                    openWants = openWants,
                    openSettings = openSettings,
                    openRelease = openRelease,
                )
                Tab.Review -> ReviewListScreen(openQuestion)
                Tab.Downloads -> DownloadsScreen(openQuestion)
                Tab.Search -> SearchScreen(openArtist, openRelease)
            }
        }
        BottomBar(tab, waiting) { tab = it }
    }
}

/** The four places, as on the web's bar: an icon with its name under it, and
 * the open one on a pill in the page's light ink. */
@Composable
private fun BottomBar(selected: Int, waiting: Int, onSelect: (Int) -> Unit) {
    val inks = LocalInks.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Duoton.panel)
            .navigationBarsPadding()
            .height(72.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { entry ->
            val on = entry.ordinal == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(entry.ordinal) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box {
                    Box(
                        Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) inks.light else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { MisprintIcon(entry.icon, tint = if (on) inks.dark else Duoton.ink3, size = 22.dp) }
                    if (entry == Tab.Review && waiting > 0) {
                        Text(
                            if (waiting > 999) "999+" else waiting.toString(),
                            style = Type.small.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFF2A1F05),
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)
                                .clip(RoundedCornerShape(9.dp)).background(Duoton.want).padding(horizontal = 5.dp),
                        )
                    }
                }
                Text(entry.label, style = Type.small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = if (on) Duoton.ink else Duoton.ink3)
            }
        }
    }
}
