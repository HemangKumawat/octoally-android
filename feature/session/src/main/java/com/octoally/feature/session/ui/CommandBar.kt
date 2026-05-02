package com.octoally.feature.session.ui

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.octoally.core.ui.*

/**
 * Gboard-proof command input bar.
 *
 * Uses BasicTextField2 with KeyboardType.Password to disable autocorrect/autocomplete
 * at the IME level — critical for terminal-style input where gboard mangles commands.
 *
 * After each submit: calls restartInput() to flush IME state so backspace history
 * doesn't bleed into the next command.
 *
 * A leading paperclip icon launches the system Photo Picker via [onAttachClick]
 * (wired from [SessionScreen]). The callback is only invoked when a callback is
 * provided and the bar is enabled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommandBar(
    state: TextFieldState,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    onAttachClick: (() -> Unit)? = null,
) {
    val view: View = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onAttachClick != null) {
            IconButton(
                onClick = onAttachClick,
                enabled = enabled,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = "Attach image",
                    tint = if (enabled) ColorAccent else ColorTextMuted
                )
            }
        }

        Text(
            text = ">",
            style = MonoCode,
            color = ColorAccent,
            modifier = Modifier.padding(end = 8.dp)
        )

        // rc15 input fix — multi-line input was eating Enter (IME inserted newlines
        // instead of triggering Send) so long messages never submitted; the next
        // Send-keyed short message would flush both at once. Now: textfield stays
        // multi-line for paste/UX, but submission is unambiguous via either
        // (a) `lineBreaks = MultiLineWithEnter` keyboard policy OR (b) the
        // explicit Send button below. We rely on the button as the canonical
        // submit path.
        val submit: () -> Unit = {
            val input = state.text.toString()
            if (input.isNotBlank() && enabled && !isBusy) {
                onSubmit(input)
                state.clearText()
                view.post {
                    view.context
                        .getSystemService(InputMethodManager::class.java)
                        ?.restartInput(view)
                }
            }
        }

        BasicTextField(
            state = state,
            enabled = enabled,
            textStyle = MonoCode.copy(color = ColorTextPrimary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrect = false,
                imeAction = ImeAction.Send,
                capitalization = KeyboardCapitalization.None
            ),
            onKeyboardAction = KeyboardActionHandler { submit() },
            modifier = Modifier.weight(1f),
            decorator = { inner ->
                if (state.text.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = ColorAccent,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                isBusy -> "Working…"
                                enabled -> "Enter command…"
                                else -> "Waiting…"
                            },
                            style = MonoCode,
                            color = ColorTextMuted
                        )
                    }
                }
                inner()
            }
        )

        IconButton(
            onClick = submit,
            enabled = enabled && !isBusy && state.text.isNotEmpty(),
            modifier = Modifier
                .size(36.dp)
                .padding(start = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Send,
                contentDescription = "Send",
                tint = if (enabled && state.text.isNotEmpty()) ColorAccent else ColorTextMuted
            )
        }
    }
}
