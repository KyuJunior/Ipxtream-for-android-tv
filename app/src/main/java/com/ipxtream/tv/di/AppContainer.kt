package com.ipxtream.tv.di

import android.app.Application
import android.content.Context
import com.ipxtream.tv.IpxApplication
import com.ipxtream.tv.data.local.CredentialStore
import com.ipxtream.tv.data.local.LibraryStore
import com.ipxtream.tv.data.model.AuthCredentials
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide singletons, built once (manual DI; no Hilt/Koin).
 *
 * One [CredentialStore] (opening encrypted storage is slow, so [IpxApplication] warms it up
 * off the main thread) and one [LibraryStore] per account, so the player and the dashboard
 * share the same favorites/history in memory instead of each reading and parsing the file.
 */
class AppContainer(private val app: Application) {

    val credentialStore: CredentialStore by lazy { CredentialStore(app) }

    private val libraries = ConcurrentHashMap<String, LibraryStore>()

    fun libraryStore(credentials: AuthCredentials): LibraryStore =
        libraries.getOrPut("${credentials.server}|${credentials.username}") { LibraryStore(app, credentials) }

    companion object {
        fun from(context: Context): AppContainer = (context.applicationContext as IpxApplication).container
    }
}
