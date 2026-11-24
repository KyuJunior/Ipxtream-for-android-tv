package com.ipxtream.tv.ui.gate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.R
import com.ipxtream.tv.data.api.AppGate
import com.ipxtream.tv.data.api.GateBlock
import com.ipxtream.tv.data.api.UpdateManager
import com.ipxtream.tv.ui.dashboard.components.PremiumButton
import com.ipxtream.tv.ui.login.LoginActivity
import com.ipxtream.tv.ui.theme.IpxTvTheme
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

/** Full-screen stop shown when [AppGate] blocks this version. Back leaves the app. */
class BlockedActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = com.ipxtream.tv.data.local.AppSettingsStore.getInstance(newBase).settings.value.appLanguage.code
        super.attachBaseContext(com.ipxtream.tv.util.LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startBlock = runCatching { GateBlock.valueOf(intent.getStringExtra(EXTRA_BLOCK).orEmpty()) }.getOrDefault(GateBlock.DISABLED)
        val startMessage = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()

        setContent {
            IpxTvTheme {
                val scope = rememberCoroutineScope()
                var status by remember { mutableStateOf<String?>(null) }
                // Retry can change the reason (e.g. offline, then "update required").
                var block by remember { mutableStateOf(startBlock) }
                var custom by remember { mutableStateOf(startMessage) }
                var busy by remember { mutableStateOf(false) }
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

                val title = stringResource(when (block) {
                    GateBlock.OUTDATED -> R.string.gate_outdated_title
                    GateBlock.DISABLED -> R.string.gate_disabled_title
                    GateBlock.UNVERIFIED -> R.string.gate_unverified_title
                })
                val body = custom.ifBlank { stringResource(when (block) {
                    GateBlock.OUTDATED -> R.string.gate_outdated_body
                    GateBlock.DISABLED -> R.string.gate_disabled_body
                    GateBlock.UNVERIFIED -> R.string.gate_unverified_body
                }) }
                val checking = stringResource(R.string.update_checking)
                val stillBlocked = stringResource(R.string.gate_still_blocked)

                Box(Modifier.fillMaxSize().background(SlateDeep), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.ic_brand_mark), null, Modifier.width(76.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(title, style = IpxTypography.HeadlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(body, style = IpxTypography.BodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
                        status?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, style = IpxTypography.BodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (block == GateBlock.OUTDATED) {
                                PremiumButton(
                                    label = stringResource(if (BuildConfig.SELF_UPDATE) R.string.update_download else R.string.update_open_play),
                                    icon = null,
                                    onClick = {
                                        if (!busy) { busy = true; scope.launch { try { status = update { status = it } } finally { busy = false } } }
                                    },
                                    modifier = Modifier.focusRequester(firstFocus)
                                )
                            }
                            PremiumButton(
                                label = stringResource(R.string.gate_retry),
                                icon = null,
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                status = checking
                                                val verdict = AppGate.check(this@BlockedActivity, force = true)
                                                if (verdict == null) reopen() else {
                                                    block = verdict.block; custom = verdict.message; status = stillBlocked
                                                }
                                            } finally { busy = false }
                                        }
                                    }
                                },
                                modifier = if (block == GateBlock.OUTDATED) Modifier else Modifier.focusRequester(firstFocus)
                            )
                        }
                    }
                }
            }
        }
    }

    /** Returns a status line, or null once the store/installer has opened. */
    private suspend fun update(progress: (String) -> Unit): String? {
        if (!BuildConfig.SELF_UPDATE) {
            for (uri in listOf("market://details?id=$packageName", "https://play.google.com/store/apps/details?id=$packageName")) {
                if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }.isSuccess) return null
            }
            return getString(R.string.update_store_unavailable)
        }
        val manager = UpdateManager(this)
        val asset = manager.checkForUpdates()?.assets?.let { a ->
            a.firstOrNull { it.name.equals("app-release.apk", true) } ?: a.firstOrNull { it.name.endsWith(".apk") }
        } ?: return getString(R.string.update_download_failed)
        val file = File(File(cacheDir, "updates").apply { mkdirs() }, "ipxtream_update.apk").apply { delete() }
        val ok = manager.downloadUpdate(asset.downloadUrl, file) { progress(getString(R.string.update_downloading_pct, (it * 100).toInt())) }
        return when {
            !ok -> getString(R.string.update_download_failed)
            !manager.isTrustedUpdate(file) -> { file.delete(); getString(R.string.update_verify_failed) }
            !manager.installApk(file) -> getString(R.string.update_installer_failed)
            else -> null
        }
    }

    private fun reopen() {
        startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    private val touchBridge by lazy { com.ipxtream.tv.ui.theme.TouchBridge(this) }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean =
        touchBridge.dispatch(event) { super.dispatchTouchEvent(it) }

    companion object {
        const val EXTRA_BLOCK = "extra_block"
        const val EXTRA_MESSAGE = "extra_message"
    }
}
