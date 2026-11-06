package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.ipxtream.tv.R
import com.ipxtream.tv.ui.theme.Accent
import com.ipxtream.tv.ui.theme.AccentRed
import com.ipxtream.tv.ui.theme.BorderSubtle
import com.ipxtream.tv.ui.theme.GlassStroke
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary
import com.ipxtream.tv.ui.theme.TouchBridgeDialogHost

/**
 * Asks for the name this player's scores are saved under. Leaving it empty, Back or "Random name"
 * gives a generated one, so every score always has a name.
 */
@Composable
internal fun PlayerNameDialog(current: String, onDone: (String) -> Unit) {
    val resources = LocalContext.current.resources
    fun randomName() = GamePlayerName.random(
        resources.getStringArray(R.array.game_name_adjectives).toList(),
        resources.getStringArray(R.array.game_name_nouns).toList()
    )
    var text by remember { mutableStateOf(current) }
    val field = remember { FocusRequester() }
    var refused by remember { mutableStateOf(false) }
    val finish = {
        val name = GamePlayerName.clean(text)
        // An offensive name stays in the field with a note, so the player can change it.
        if (name.isNotEmpty() && GameNameFilter.isBlocked(name)) refused = true
        else onDone(name.ifEmpty { randomName() })
    }

    Dialog(onDismissRequest = finish, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TouchBridgeDialogHost {
            Box(Modifier.fillMaxSize().background(SlateDeep.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .padding(16.dp)
                        .widthIn(max = 560.dp)
                        .background(SlateCard, RoundedCornerShape(18.dp))
                        .border(1.dp, GlassStroke, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.game_player_title), style = IpxTypography.HeadlineMedium, color = TextPrimary)
                    Text(stringResource(R.string.game_player_hint), style = IpxTypography.BodyMedium, color = TextSecondary)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(GamePlayerName.MAX); refused = false },
                        placeholder = { Text(stringResource(R.string.game_player_field), color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextSecondary,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderSubtle,
                            cursorColor = Accent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().focusRequester(field)
                    )
                    if (refused) {
                        Text(stringResource(R.string.game_player_blocked), style = IpxTypography.BodyMedium, color = AccentRed)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillChip(label = stringResource(R.string.game_player_save), selected = true, onClick = finish)
                        PillChip(label = stringResource(R.string.game_player_random), selected = false, onClick = { onDone(randomName()) })
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { field.requestFocus() }
    }
}
