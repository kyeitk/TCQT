package com.owo233.tcqt.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.ui.component.MaterialTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        SearchInputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {},
            expanded = true,
            onExpandedChange = { expanded ->
                if (!expanded) onBackClick()
            },
            modifier = Modifier
                .weight(1f),
            label = "搜索功能名称或描述",
        )
    }
}

@Composable
internal fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
) {
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    val labelText by remember(query, expanded, label) {
        derivedStateOf { if (!(query.isNotEmpty() || expanded)) label else "" }
    }

    val textColor = LocalContentColor.current
    val inputTextStyle = MiuixTheme.textStyles.main
        .copy(fontWeight = FontWeight.Medium)
        .copy(color = textColor)

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            currentOnQueryChange(newValue.text)
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) currentOnExpandedChange(true) }
            .semantics {
                onClick {
                    focusRequester.requestFocus()
                    true
                }
            },
        enabled = enabled,
        singleLine = true,
        textStyle = inputTextStyle,
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { currentOnSearch(query) }),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.padding(
                            start = SearchBarDefaults.LeadingIconStartPadding,
                            end = SearchBarDefaults.LeadingIconEndPadding,
                        ),
                        imageVector = MiuixIcons.Basic.Search,
                        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        contentDescription = "搜索",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SearchBarDefaults.InputFieldMinHeight),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (labelText.isNotEmpty()) {
                            Text(
                                text = labelText,
                                fontSize = SearchBarDefaults.InputFieldFontSize,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                            )
                        }
                        innerTextField()
                    }
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                start = SearchBarDefaults.TrailingIconStartPadding,
                                end = SearchBarDefaults.TrailingIconEndPadding,
                            ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { currentOnQueryChange("") },
                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                tint = MiuixTheme.colorScheme.onSurfaceContainerHighest,
                                contentDescription = "清除搜索",
                            )
                        }
                    }
                }
            }
        },
    )

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else if (focused) {
            if (query.isNotEmpty()) {
                currentOnQueryChange("")
            }
            focusManager.clearFocus()
        }
    }
}
