package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextPrimary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) SlateGlass else SlateCard,
        label = "searchBgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) AccentCyan else Color.Transparent,
        label = "searchBorderColor"
    )

    // Scale animation similar to TV Cards
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "searchScale"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(bgColor, RoundedCornerShape(26.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(26.dp)
            )
            .onFocusChanged { state -> isFocused = state.isFocused },
        textStyle = IpxTypography.BodyMedium.copy(color = TextPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() },
            onDone = { keyboardController?.hide() }
        ),
        cursorBrush = SolidColor(AccentCyan),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔍",
                    style = IpxTypography.TitleMedium,
                    color = if (isFocused) AccentCyan else TextMuted
                )
                Spacer(modifier = Modifier.width(12.dp))

                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = IpxTypography.BodyMedium,
                            color = TextMuted
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}
