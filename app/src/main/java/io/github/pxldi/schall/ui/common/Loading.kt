package io.github.pxldi.schall.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.pxldi.schall.data.Api
import io.github.pxldi.schall.data.LiveUpdates
import io.github.pxldi.schall.data.Stale
import io.github.pxldi.schall.data.text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The signed-in client, provided below the sign-in gate. */
val LocalApi = compositionLocalOf<Api> { error("no session") }
val LocalLive = compositionLocalOf<LiveUpdates> { error("no live updates") }

/** The activity's store, so a list and the question opened from it share one
 * loader and one answer. */
val LocalAppStore = compositionLocalOf<ViewModelStoreOwner> { error("no store owner") }

/** What a screen has of a server answer. Failed is only ever the first
 * answer: once something was read, a refresh that fails keeps what was read
 * and says so beside it. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** One server list, kept across rotation, refetched on demand. */
class Loader<T>(private val fetch: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _stale = MutableStateFlow("")
    /** What went wrong on the last refresh, while older data is still shown. */
    val stale: StateFlow<String> = _stale.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (_refreshing.value) return@launch
            _refreshing.value = true
            try {
                _state.value = Load.Ready(fetch())
                _stale.value = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                if (_state.value is Load.Ready) _stale.value = failed.text()
                else _state.value = Load.Failed(failed.text())
            } finally {
                _refreshing.value = false
            }
        }
    }
}

/** Refreshes while the screen is on: on every notice for one of its topics,
 * every 15 s as the safety net, and once each time it comes back to the
 * front. Nothing runs in the background, the same as on the web. */
@Composable
fun KeepFresh(vararg topics: Stale, refresh: () -> Unit) {
    val live = LocalLive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(live, topics.toList()) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { live.notices(*topics).collect { refresh() } }
            launch {
                while (true) {
                    delay(15_000)
                    refresh()
                }
            }
            refresh()
        }
    }
}

/** A control's press: busy while the server answers, an error under it when
 * the server refused, and what to make stale once it agreed. */
class Action(private val scope: CoroutineScope, private val live: LiveUpdates) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    fun run(vararg then: Stale, done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = ""
            try {
                block()
                for (stale in then) live.invalidate(stale)
                done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                error = failed.text()
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberAction(): Action {
    val scope = rememberCoroutineScope()
    val live = LocalLive.current
    return remember(scope, live) { Action(scope, live) }
}
