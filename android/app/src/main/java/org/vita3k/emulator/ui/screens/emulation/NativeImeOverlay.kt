package org.vita3k.emulator.ui.screens.emulation

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.vita3k.emulator.Emulator
import org.vita3k.emulator.NativeLib
import org.vita3k.emulator.R
import org.vita3k.emulator.data.NativeImeState
import org.vita3k.emulator.ui.theme.Vita3KTheme
import org.vita3k.emulator.ui.viewmodel.EmulationSessionViewModel

private val ImeOverlayBackground = Color.Black.copy(alpha = 0.30f)
private val ImeOverlayText = Color.Black.copy(alpha = 0.96f)
private val ImeOverlayPlaceholder = Color.Black.copy(alpha = 0.70f)
private val ImeOverlayAccent = Color.Black.copy(alpha = 0.96f)

object NativeImeOverlayHost {
    @JvmStatic
    fun attach(
        composeView: ComposeView,
        sessionViewModel: EmulationSessionViewModel
    ) {
        composeView.setContent {
            Vita3KTheme {
                NativeImeOverlay(
                    sessionViewModel = sessionViewModel,
                    hostView = composeView
                )
            }
        }
    }
}

@Composable
private fun NativeImeOverlay(
    sessionViewModel: EmulationSessionViewModel,
    hostView: ComposeView
) {
    val uiState = sessionViewModel.uiState
    val imeState = sessionViewModel.imeState
    val context = LocalContext.current

    val state = imeState
    val visible = state?.sceImeActive == true
        && state.dialogActive.not()
        && !uiState.showMenu
        && !uiState.isEditingControls
        && (context as? Emulator)?.isNativeImeOverlaySuppressed() != true

    SideEffect {
        hostView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    LaunchedEffect(visible, imeState?.imeKeyboardMode) {
        if (visible) {
            (context as? Emulator)?.syncImeKeyboardMode(imeState?.imeKeyboardMode ?: 1)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut(tween(140)) + slideOutVertically(targetOffsetY = { -it / 3 })
    ) {
        state?.let { current ->
            val useCustomKeyboard = current.imeKeyboardMode == 0
            var useUppercase by remember(current.imeKeyboardMode) { mutableStateOf(false) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.displayCutout.asPaddingValues())
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val gameViewportWidth = minOf(maxWidth, maxHeight * (960f / 544f))
                val preview = buildImePreview(current)

                Surface(
                    modifier = Modifier.width(gameViewportWidth),
                    shape = RoundedCornerShape(22.dp),
                    color = ImeOverlayBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val buttonInset = if (current.enterLabel.isNotBlank()) 96.dp else 0.dp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = buttonInset, end = buttonInset),
                            contentAlignment = Alignment.Center
                        ) {
                            if (preview.text.isBlank()) {
                                Text(
                                    text = stringResource(R.string.emulation_ime_placeholder),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ImeOverlayPlaceholder,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ImeOverlayText,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (useCustomKeyboard) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                imeLetterRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), useUppercase)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Spacer(modifier = Modifier.weight(0.5f))
                                    imeLetterKey("a", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("s", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("d", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("f", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("g", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("h", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("j", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("k", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("l", useUppercase, Modifier.weight(1f))
                                    Spacer(modifier = Modifier.weight(0.5f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(
                                        onClick = { useUppercase = !useUppercase },
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ImeOverlayBackground)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.emulation_ime_key_shift),
                                            color = ImeOverlayText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    imeLetterKey("z", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("x", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("c", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("v", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("b", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("n", useUppercase, Modifier.weight(1f))
                                    imeLetterKey("m", useUppercase, Modifier.weight(1f))
                                    TextButton(
                                        onClick = { runCatching { NativeLib.imeBackspace() } },
                                        modifier = Modifier
                                            .weight(1.8f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ImeOverlayBackground)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.emulation_ime_key_backspace),
                                            color = ImeOverlayText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    imeDigitKey("1", Modifier.weight(1f))
                                    imeDigitKey("2", Modifier.weight(1f))
                                    imeDigitKey("3", Modifier.weight(1f))
                                    imeDigitKey("4", Modifier.weight(1f))
                                    imeDigitKey("5", Modifier.weight(1f))
                                    imeDigitKey("6", Modifier.weight(1f))
                                    imeDigitKey("7", Modifier.weight(1f))
                                    imeDigitKey("8", Modifier.weight(1f))
                                    imeDigitKey("9", Modifier.weight(1f))
                                    imeDigitKey("0", Modifier.weight(1f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextButton(
                                        onClick = { runCatching { NativeLib.dismissIme() } },
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ImeOverlayBackground)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.emulation_ime_key_close),
                                            color = ImeOverlayText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    TextButton(
                                        onClick = { commitImeKey(" ", false) },
                                        modifier = Modifier
                                            .weight(2.4f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ImeOverlayBackground)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.emulation_ime_key_space),
                                            color = ImeOverlayText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                    TextButton(
                                        onClick = { runCatching { NativeLib.submitIme() } },
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(ImeOverlayBackground)
                                    ) {
                                        Text(
                                            text = if (current.enterLabel.isNotBlank()) current.enterLabel else stringResource(R.string.emulation_ime_key_enter),
                                            color = ImeOverlayText,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        } else if (current.enterLabel.isNotBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                TextButton(
                                    onClick = { runCatching { NativeLib.submitIme() } },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(ImeOverlayBackground)
                                ) {
                                    Text(
                                        text = current.enterLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = ImeOverlayText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun commitImeKey(text: String, useUppercase: Boolean) {
    val value = if (useUppercase) text.uppercase() else text
    if (value.isNotEmpty()) {
        runCatching { NativeLib.commitImeText(value) }
    }
}

@Composable
private fun RowScope.imeLetterRow(labels: List<String>, useUppercase: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { label ->
            imeLetterKey(label, useUppercase, Modifier.weight(1f))
        }
    }
}

@Composable
private fun imeLetterKey(
    label: String,
    useUppercase: Boolean,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = { commitImeKey(label, useUppercase) },
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ImeOverlayBackground)
    ) {
        Text(
            text = if (useUppercase) label.uppercase() else label,
            color = ImeOverlayText,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun imeDigitKey(
    label: String,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = { commitImeKey(label, false) },
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ImeOverlayBackground)
    ) {
        Text(
            text = label,
            color = ImeOverlayText,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildImePreview(state: NativeImeState): AnnotatedString {
    val text = state.text
    val caretIndex = state.caretIndex.coerceIn(0, text.length)
    val preeditStart = state.preeditStart.coerceIn(0, text.length)
    val preeditEnd = (preeditStart + state.preeditLength).coerceIn(preeditStart, text.length)
    val caretStyle = SpanStyle(
        color = ImeOverlayAccent,
        fontWeight = FontWeight.Bold
    )
    val preeditStyle = SpanStyle(
        color = ImeOverlayAccent,
        textDecoration = TextDecoration.Underline
    )

    return buildAnnotatedString {
        fun appendCaret(position: Int) {
            if (caretIndex == position) {
                withStyle(caretStyle) {
                    append('|')
                }
            }
        }

        if (text.isEmpty()) {
            appendCaret(0)
            return@buildAnnotatedString
        }

        appendCaret(0)

        var cursor = 0
        while (cursor < text.length) {
            val segmentEnd = when {
                cursor < preeditStart -> preeditStart
                cursor < preeditEnd -> preeditEnd
                else -> text.length
            }
            val segment = text.substring(cursor, segmentEnd)
            if (cursor >= preeditStart && cursor < preeditEnd) {
                withStyle(preeditStyle) {
                    append(segment)
                }
            } else {
                append(segment)
            }
            cursor = segmentEnd
            appendCaret(cursor)
        }
    }
}
