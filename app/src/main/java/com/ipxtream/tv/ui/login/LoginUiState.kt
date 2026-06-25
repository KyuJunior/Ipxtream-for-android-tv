package com.ipxtream.tv.ui.login

import com.ipxtream.tv.data.model.XtreamAuthResponse

/**
 * Sealed hierarchy representing every possible state of the login screen.
 *
 * Observed via [LoginViewModel.uiState] (a StateFlow) in the Activity/Fragment.
 */
sealed class LoginUiState {

    /** Initial state — no action has been taken yet. */
    object Idle : LoginUiState()

    /** A login attempt is in-flight. Show a progress indicator. */
    object Loading : LoginUiState()

    /**
     * Authentication succeeded.
     * @param authResponse Full server + user info from the Xtream Codes API.
     */
    data class Success(val authResponse: XtreamAuthResponse) : LoginUiState()

    /**
     * Authentication failed or a network error occurred.
     * @param message Human-readable description safe to display directly in the UI.
     */
    data class Error(val message: String) : LoginUiState()
}
