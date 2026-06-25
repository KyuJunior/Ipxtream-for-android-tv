package com.ipxtream.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.ipxtream.tv.ui.theme.AccentCyan
import com.ipxtream.tv.ui.theme.AccentCyanGlow
import com.ipxtream.tv.ui.theme.AccentAmber
import com.ipxtream.tv.ui.theme.BorderSubtle
import com.ipxtream.tv.ui.theme.IpxTypography
import com.ipxtream.tv.ui.theme.SlateCard
import com.ipxtream.tv.ui.theme.SlateDeep
import com.ipxtream.tv.ui.theme.SlateGlass
import com.ipxtream.tv.ui.theme.TextMuted
import com.ipxtream.tv.ui.theme.TextOnAccent
import com.ipxtream.tv.ui.theme.TextPrimary
import com.ipxtream.tv.ui.theme.TextSecondary

/**
 * Login screen — glass panel centred on a dark gradient background.
 *
 * ## Layout
 * ```
 * ┌──────────── full screen dark gradient ─────────────────┐
 * │                                                        │
 * │          ┌─── glass card (max 460dp wide) ──┐          │
 * │          │  IPXtream TV  [logo text]         │          │
 * │          │                                   │          │
 * │          │  Server URL  [_________________]  │          │
 * │          │  Username    [_________________]  │          │
 * │          │  Password    [_________________]  │          │
 * │          │                                   │          │
 * │          │  [error message if any]            │          │
 * │          │                                   │          │
 * │          │          [  Sign In  ]             │          │
 * │          └───────────────────────────────────┘          │
 * │                                                        │
 * └────────────────────────────────────────────────────────┘
 * ```
 *
 * ## D-Pad navigation
 * - Fields are in a vertical sequence; DPAD_DOWN / IME_NEXT moves focus down.
 * - DPAD_UP moves focus up.
 * - From Password field, DPAD_DOWN focuses the Sign In button.
 * - ENTER / DPAD_CENTER on Sign In calls [onLogin].
 *
 * @param uiState   Current [LoginUiState] from [LoginViewModel].
 * @param onLogin   Called with (server, username, password) when Sign In pressed.
 * @param savedServer   Pre-filled server from [CredentialStore], if any.
 * @param savedUsername Pre-filled username from [CredentialStore], if any.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AmbientBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateDeep)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw a deep cyan/blue ambient orb at top-left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentCyan.copy(alpha = pulse1), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.15f, height * 0.2f),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = androidx.compose.ui.geometry.Offset(width * 0.15f, height * 0.2f)
            )
            
            // Draw a deep amber ambient orb at bottom-right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentAmber.copy(alpha = pulse2 * 0.6f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.8f),
                    radius = width * 0.6f
                ),
                radius = width * 0.6f,
                center = androidx.compose.ui.geometry.Offset(width * 0.85f, height * 0.8f)
            )
        }
        
        // Semi-transparent overlay to ensure readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState:       LoginUiState,
    onLogin:       (server: String, username: String, password: String) -> Unit,
    savedServer:   String = "",
    savedUsername: String = ""
) {
    var server   by remember { mutableStateOf(savedServer) }
    var username by remember { mutableStateOf(savedUsername) }
    var password by remember { mutableStateOf("") }

    val isLoading    = uiState is LoginUiState.Loading
    val errorMessage = (uiState as? LoginUiState.Error)?.message

    // Focus requesters for D-Pad navigation between fields
    val serverFocuser   = remember { FocusRequester() }
    val usernameFocuser = remember { FocusRequester() }
    val passwordFocuser = remember { FocusRequester() }
    val buttonFocuser   = remember { FocusRequester() }
    val focusManager    = LocalFocusManager.current

    // Give initial focus to the server field (or username if server is pre-filled).
    LaunchedEffect(Unit) {
        runCatching {
            if (savedServer.isNotBlank()) usernameFocuser.requestFocus()
            else serverFocuser.requestFocus()
        }
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "cardAlpha"
    )
    val cardOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 40.dp,
        animationSpec = tween(1200, easing = EaseOutCubic),
        label = "cardOffset"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 150, easing = EaseOutCubic),
        label = "logoAlpha"
    )
    val f1Alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 300, easing = EaseOutCubic),
        label = "f1"
    )
    val f2Alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 450, easing = EaseOutCubic),
        label = "f2"
    )
    val f3Alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 600, easing = EaseOutCubic),
        label = "f3"
    )
    val btnAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(800, delayMillis = 750, easing = EaseOutCubic),
        label = "btn"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AmbientBackground()

        // ── Glass card ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.88f)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardOffset.toPx()
                }
                .clip(RoundedCornerShape(20.dp))
                .background(SlateCard.copy(alpha = 0.85f))
                .padding(horizontal = 36.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Logo / title area ─────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha; translationY = (1f - logoAlpha) * 15.dp.toPx() }
            ) {
                Text(
                    text       = "IPXtream",
                    fontSize   = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = AccentCyan
                )
                Text(
                    text  = "TV",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    letterSpacing = 6.sp
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── Server URL field ──────────────────────────────────────────────
            Box(
                modifier = Modifier.graphicsLayer { alpha = f1Alpha; translationY = (1f - f1Alpha) * 15.dp.toPx() }
            ) {
                LoginField(
                    label       = "Server URL",
                    hint        = "http://your-provider.com:8080",
                    value       = server,
                    onValueChange = { server = it },
                    keyboardType  = KeyboardType.Uri,
                    imeAction     = ImeAction.Next,
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) },
                    focusRequester = serverFocuser,
                    enabled     = !isLoading
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Username field ────────────────────────────────────────────────
            Box(
                modifier = Modifier.graphicsLayer { alpha = f2Alpha; translationY = (1f - f2Alpha) * 15.dp.toPx() }
            ) {
                LoginField(
                    label         = "Username",
                    hint          = "your_username",
                    value         = username,
                    onValueChange = { username = it },
                    keyboardType  = KeyboardType.Text,
                    imeAction     = ImeAction.Next,
                    onNext        = { focusManager.moveFocus(FocusDirection.Down) },
                    focusRequester = usernameFocuser,
                    enabled       = !isLoading
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Password field ────────────────────────────────────────────────
            Box(
                modifier = Modifier.graphicsLayer { alpha = f3Alpha; translationY = (1f - f3Alpha) * 15.dp.toPx() }
            ) {
                LoginField(
                    label         = "Password",
                    hint          = "••••••••",
                    value         = password,
                    onValueChange = { password = it },
                    keyboardType  = KeyboardType.Password,
                    imeAction     = ImeAction.Done,
                    onNext        = { runCatching { buttonFocuser.requestFocus() } },
                    focusRequester = passwordFocuser,
                    isPassword    = true,
                    enabled       = !isLoading
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Error message ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = errorMessage != null,
                enter   = fadeIn(),
                exit    = fadeOut()
            ) {
                Text(
                    text     = errorMessage ?: "",
                    style    = IpxTypography.BodyMedium,
                    color    = Color(0xFFFF6B6B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FF4444))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(8.dp))

            // ── Sign In button ────────────────────────────────────────────────
            Box(
                modifier = Modifier.graphicsLayer { alpha = btnAlpha; translationY = (1f - btnAlpha) * 15.dp.toPx() }
            ) {
                Button(
                    onClick  = {
                        if (!isLoading) onLogin(server.trim(), username.trim(), password)
                    },
                    enabled  = !isLoading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .focusRequester(buttonFocuser),
                    colors   = ButtonDefaults.colors(
                        containerColor        = AccentCyan,
                        focusedContainerColor = AccentCyan,
                        disabledContainerColor = SlateGlass
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.03f)
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(18.dp),
                                color     = TextOnAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Connecting…", color = TextOnAccent, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            "Sign In",
                            color      = TextOnAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
//  Shared field component
// =============================================================================

@Composable
private fun LoginField(
    label:         String,
    hint:          String,
    value:         String,
    onValueChange: (String) -> Unit,
    keyboardType:  KeyboardType,
    imeAction:     ImeAction,
    onNext:        () -> Unit,
    focusRequester: FocusRequester,
    isPassword:    Boolean = false,
    enabled:       Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(220, easing = EaseInOutCubic),
        label = "fieldScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Text(
            text  = label,
            style = IpxTypography.LabelSmall,
            color = if (isFocused) AccentCyan else TextMuted
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            placeholder   = { Text(hint, color = TextMuted) },
            singleLine    = true,
            enabled       = enabled,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword && passwordVisible) KeyboardType.Text else keyboardType,
                imeAction    = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
                onDone = { onNext() }
            ),
            trailingIcon = if (isPassword) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible }
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = if (isFocused) AccentCyan else TextMuted
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor      = TextPrimary,
                unfocusedTextColor    = TextSecondary,
                focusedBorderColor    = AccentCyan,
                unfocusedBorderColor  = BorderSubtle,
                cursorColor           = AccentCyan,
                focusedLabelColor     = AccentCyan,
                focusedContainerColor = SlateCard.copy(alpha = 0.85f),
                unfocusedContainerColor = SlateCard.copy(alpha = 0.4f),
                disabledContainerColor  = SlateCard.copy(alpha = 0.1f)
            ),
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
        )
    }
}
