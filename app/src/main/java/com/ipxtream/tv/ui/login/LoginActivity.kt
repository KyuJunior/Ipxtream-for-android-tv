package com.ipxtream.tv.ui.login

import androidx.compose.runtime.getValue

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ipxtream.tv.data.local.CredentialStore
import com.ipxtream.tv.ui.dashboard.DashboardActivity
import com.ipxtream.tv.ui.theme.IpxTvTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Entry-point Activity for the app.
 *
 * ## Responsibilities
 * 1. On cold start: check [CredentialStore] for saved credentials and call
 *    [LoginViewModel.tryAutoLogin] — if the server is reachable the user goes
 *    straight to [DashboardActivity] without entering anything.
 * 2. On explicit login: collect [LoginUiState.Success] and navigate to
 *    [DashboardActivity], passing credentials as Intent extras.
 * 3. On logout (back-stack return from Dashboard): show the login form again.
 *
 * ## Why pass credentials as Intent extras?
 * [DashboardActivity] needs the credentials to build Xtream Codes stream URLs.
 * Passing them via Intent extras (instead of re-loading from [CredentialStore])
 * avoids a second disk read and keeps the URL-building logic independent of
 * whether the user just logged in or was auto-logged in.
 *
 * The extras are short-lived — they exist only for the Activity launch transition
 * and are not logged or persisted anywhere.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var credentialStore: CredentialStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialStore = CredentialStore(applicationContext)

        viewModel = ViewModelProvider(
            this,
            LoginViewModelFactory(credentialStore)
        )[LoginViewModel::class.java]

        val isAddAccountMode = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)

        // ── Observe success → navigate to Dashboard ───────────────────────────
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state is LoginUiState.Success) {
                    openDashboard(state)
                }
            }
        }

        // ── Try auto-login from saved credentials ─────────────────────────────
        if (!isAddAccountMode) {
            viewModel.tryAutoLogin()
        }

        // ── Compose UI ────────────────────────────────────────────────────────
        setContent {
            IpxTvTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Pre-fill fields with saved credentials if available (not in add account mode)
                val saved = if (isAddAccountMode) null else credentialStore.loadCredentials()

                LoginScreen(
                    uiState       = uiState,
                    savedServer   = saved?.server   ?: "",
                    savedUsername = saved?.username ?: "",
                    onLogin       = { server, username, password ->
                        viewModel.login(server, username, password)
                    }
                )
            }
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun openDashboard(success: LoginUiState.Success) {
        val creds = credentialStore.loadCredentials() ?: return

        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_SERVER,   creds.server)
            putExtra(DashboardActivity.EXTRA_USERNAME, creds.username)
            putExtra(DashboardActivity.EXTRA_PASSWORD, creds.password)
            // Don't allow back-navigation to this Activity after login.
            // The user logs out via the Dashboard menu instead.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        const val EXTRA_ADD_ACCOUNT = "extra_add_account"
    }
}
