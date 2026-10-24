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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    override fun attachBaseContext(newBase: android.content.Context) {
        val appSettingsStore = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(newBase)
        val langCode = appSettingsStore.settings.value.appLanguage.code
        val localizedContext = com.ipxtream.tv.util.LocaleHelper.wrap(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialStore = com.ipxtream.tv.di.AppContainer.from(this).credentialStore

        viewModel = ViewModelProvider(
            this,
            LoginViewModelFactory(credentialStore, applicationContext)
        )[LoginViewModel::class.java]

        val isAddAccountMode = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)

        // The brand intro plays only on a launch from the TV home screen, not after logout or
        // when adding an account. Auto-login runs underneath it; navigation waits for it to end.
        val showIntro = savedInstanceState == null && !isAddAccountMode &&
            intent.action == Intent.ACTION_MAIN
        val introDone = MutableStateFlow(!showIntro)

        // ── Observe success → navigate to Dashboard ───────────────────────────
        lifecycleScope.launch {
            combine(viewModel.uiState, introDone) { state, done -> state.takeIf { done } }
                .collectLatest { state ->
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

                val introFinished by introDone.collectAsStateWithLifecycle()

                // The form composes after the intro so its field doesn't take focus (and open the
                // keyboard) underneath it.
                if (introFinished) {
                    LoginScreen(
                        uiState       = uiState,
                        savedServer   = saved?.server   ?: "",
                        savedUsername = saved?.username ?: "",
                        onLogin       = { server, username, password ->
                            viewModel.login(server, username, password)
                        },
                        onDemo        = com.ipxtream.tv.data.api.DemoCatalog.SERVER.takeIf { it.isNotEmpty() }?.let { demo ->
                            {
                                viewModel.login(demo, com.ipxtream.tv.data.api.DemoCatalog.USERNAME, com.ipxtream.tv.data.api.DemoCatalog.PASSWORD)
                            }
                        }
                    )
                } else {
                    com.ipxtream.tv.ui.intro.BrandIntro(onFinished = { introDone.value = true })
                }
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
            // A tapped match reminder opens straight on Matches.
            putExtra(DashboardActivity.EXTRA_OPEN_MATCHES, this@LoginActivity.intent.getBooleanExtra(EXTRA_OPEN_MATCHES, false))
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
        const val EXTRA_OPEN_MATCHES = "extra_open_matches"
    }
}
