package com.ipxtream.tv.ui.login

import com.ipxtream.tv.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipxtream.tv.data.api.ApiClient
import com.ipxtream.tv.data.local.CredentialStore
import com.ipxtream.tv.data.model.AuthCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * ViewModel for the Login screen.
 *
 * Responsibilities:
 * 1. Validates and normalises the server URL.
 * 2. Calls [XtreamApiService.authenticate] via a Coroutine.
 * 3. Persists credentials to [CredentialStore] on success.
 * 4. Exposes all UI state through [uiState] (a cold StateFlow).
 *
 * The ViewModel itself holds no Android framework references - it receives
 * [CredentialStore] via constructor injection, which makes it trivially testable.
 *
 * @param credentialStore Encrypted credential persistence layer.
 */
class LoginViewModel(
    private val credentialStore: CredentialStore,
    private val appContext: android.content.Context
) : ViewModel() {

    private fun text(id: Int, vararg args: Any): String =
        com.ipxtream.tv.util.LocaleHelper.localized(appContext).getString(id, *args)

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    /**
     * The UI collects this flow and renders itself based on the current state.
     *
     * Exposed as an immutable [StateFlow] so only this ViewModel can mutate it.
     */
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initiates an authentication attempt against the Xtream Codes API.
     *
     * Transitions:
     *   [LoginUiState.Idle] → [LoginUiState.Loading]
     *                       → [LoginUiState.Success] (auth == 1)
     *                       → [LoginUiState.Error]   (auth == 0, HTTP error, or network failure)
     *
     * @param server   Raw server URL as typed by the user (normalised internally).
     * @param username Portal username.
     * @param password Portal password.
     */
    fun login(server: String, username: String, password: String, autoLogin: Boolean = false) {
        // Guard: don't launch a second request while one is in-flight.
        if (_uiState.value is LoginUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            val normalizedServer = ApiClient.normalizeUrl(server)
            var credentials      = AuthCredentials(normalizedServer, username, password)

            try {
                val apiService = ApiClient.create(normalizedServer, debug = com.ipxtream.tv.BuildConfig.DEBUG)
                val response   = apiService.authenticate(username, password)

                if (response.userInfo.auth == 1) {
                    if (!autoLogin) credentials = httpsUpgrade(credentials, response.serverInfo.httpsPort) ?: credentials
                    // Save ONLY after a confirmed successful authentication.
                    credentialStore.saveAccount(credentials)
                    credentialStore.saveCredentials(credentials)
                    _uiState.value = LoginUiState.Success(response)
                } else {
                    // The server responded but rejected the credentials.
                    _uiState.value = LoginUiState.Error(
                        text(R.string.login_auth_failed)
                    )
                }

            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401, 403 -> text(R.string.login_invalid)
                    429 -> text(R.string.login_too_many)
                    in 500..599 -> text(R.string.login_server_offline)
                    else -> text(R.string.login_server_error, e.code(), e.message())
                }
                _uiState.value = LoginUiState.Error(msg)
            } catch (e: IOException) {
                // Covers: no network, DNS failure, socket timeout, malformed URL.
                _uiState.value = LoginUiState.Error(
                    text(R.string.login_network)
                )
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.localizedMessage?.let(ApiClient::redactCredentials) ?: text(R.string.login_unexpected)
                )
            }
        }
    }

    /**
     * For a new HTTP account whose provider also offers HTTPS (`server_info.https_port`): the same
     * login over HTTPS, if that works within a few seconds. Null keeps the HTTP address.
     *
     * Existing accounts are never moved: favourites, history and caches are keyed on the server
     * address, so changing its scheme would hide them.
     */
    private suspend fun httpsUpgrade(credentials: AuthCredentials, httpsPort: String?): AuthCredentials? {
        val https = ApiClient.httpsVariant(credentials.server, httpsPort) ?: return null
        val known = credentialStore.getAccounts().any {
            it.username.equals(credentials.username, ignoreCase = true) &&
                ApiClient.normalizeUrl(it.server).equals(credentials.server, ignoreCase = true)
        }
        if (known) return null
        return runCatching {
            kotlinx.coroutines.withTimeoutOrNull(HTTPS_PROBE_MS) {
                ApiClient.create(https, debug = com.ipxtream.tv.BuildConfig.DEBUG)
                    .authenticate(credentials.username, credentials.password)
            }
        }.getOrNull()
            ?.takeIf { it.userInfo.auth == 1 }
            ?.let { credentials.copy(server = https) }
    }

    private companion object {
        /** Panels with a broken certificate or a closed HTTPS port must not hold up sign-in. */
        const val HTTPS_PROBE_MS = 4_000L
    }

    /**
     * Checks for previously saved credentials and, if present, silently attempts
     * to re-authenticate.  Call this from [Activity.onCreate] for auto-login.
     *
     * If credentials are found and the server is reachable the state will
     * transition all the way to [LoginUiState.Success] without any user input.
     */
    fun tryAutoLogin() {
        val saved = credentialStore.getDefaultAccount() ?: credentialStore.loadCredentials() ?: return
        credentialStore.saveCredentials(saved)
        login(saved.server, saved.username, saved.password, autoLogin = true)
    }

    /**
     * Clears persisted credentials and resets the UI to [LoginUiState.Idle].
     * Call this when the user explicitly logs out.
     */
    fun logout() {
        credentialStore.clearCredentials()
        _uiState.value = LoginUiState.Idle
    }
}
