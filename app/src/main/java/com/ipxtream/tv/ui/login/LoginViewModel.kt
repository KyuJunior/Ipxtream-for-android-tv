package com.ipxtream.tv.ui.login

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
 * The ViewModel itself holds no Android framework references — it receives
 * [CredentialStore] via constructor injection, which makes it trivially testable.
 *
 * @param credentialStore Encrypted credential persistence layer.
 */
class LoginViewModel(
    private val credentialStore: CredentialStore
) : ViewModel() {

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
    fun login(server: String, username: String, password: String) {
        // Guard: don't launch a second request while one is in-flight.
        if (_uiState.value is LoginUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading

            val normalizedServer = ApiClient.normalizeUrl(server)
            val credentials      = AuthCredentials(normalizedServer, username, password)

            try {
                val apiService = ApiClient.create(normalizedServer, debug = true)
                val response   = apiService.authenticate(username, password)

                if (response.userInfo.auth == 1) {
                    // Save ONLY after a confirmed successful authentication.
                    credentialStore.saveCredentials(credentials)
                    _uiState.value = LoginUiState.Success(response)
                } else {
                    // The server responded but rejected the credentials.
                    _uiState.value = LoginUiState.Error(
                        "Authentication failed — please check your username and password."
                    )
                }

            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401, 403 -> "Invalid credentials or expired subscription."
                    429 -> "Too many requests. Please wait a moment before trying again."
                    in 500..599 -> "The IPTV provider's server is currently offline."
                    else -> "Server error ${e.code()}: ${e.message()}"
                }
                _uiState.value = LoginUiState.Error(msg)
            } catch (e: IOException) {
                // Covers: no network, DNS failure, socket timeout, malformed URL.
                _uiState.value = LoginUiState.Error(
                    "Network error — check the server URL and your internet connection."
                )
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.localizedMessage ?: "An unexpected error occurred."
                )
            }
        }
    }

    /**
     * Checks for previously saved credentials and, if present, silently attempts
     * to re-authenticate.  Call this from [Activity.onCreate] for auto-login.
     *
     * If credentials are found and the server is reachable the state will
     * transition all the way to [LoginUiState.Success] without any user input.
     */
    fun tryAutoLogin() {
        val saved = credentialStore.loadCredentials() ?: return
        login(saved.server, saved.username, saved.password)
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
