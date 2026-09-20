package io.github.pxldi.schall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "session")

/** The signed-in session, with the name the token was minted under. It lives
 * in the app's private storage: the token is the phone's own credential, the
 * same as a browser's cookie jar, and it is revoked from the web when the
 * phone is lost. */
data class StoredSession(val server: String, val token: String, val actor: String) {
    val session get() = Session(server, token)
}

class SessionStore(private val context: Context) {
    val current: Flow<StoredSession?> = context.store.data.map { prefs ->
        val server = prefs[SERVER]
        val token = prefs[TOKEN]
        if (server.isNullOrEmpty() || token.isNullOrEmpty()) null
        else StoredSession(server, token, prefs[ACTOR] ?: "")
    }

    suspend fun save(session: StoredSession) {
        context.store.edit { prefs ->
            prefs[SERVER] = session.server
            prefs[TOKEN] = session.token
            prefs[ACTOR] = session.actor
        }
    }

    suspend fun clear() {
        context.store.edit { it.clear() }
    }

    private companion object {
        val SERVER = stringPreferencesKey("server")
        val TOKEN = stringPreferencesKey("token")
        val ACTOR = stringPreferencesKey("actor")
    }
}
