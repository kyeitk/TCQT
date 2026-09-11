package com.owo233.tcqt.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.ui.component.MaterialTheme
import com.owo233.tcqt.ui.settings.model.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.overlay.OverlayListPopup

internal enum class SettingsTopBarMode { Search, ErrorOverview, SubPage, Root }
internal data class SettingsTopBarState(
    val mode: SettingsTopBarMode,
    val path: String,
    val breadcrumbs: List<BreadcrumbItem>,
    val searchQuery: String
) {

    val animationKey: String
        get() = when (mode) {
            SettingsTopBarMode.Search -> "search"
            SettingsTopBarMode.ErrorOverview -> "errors"
            SettingsTopBarMode.SubPage -> "sub_$path"
            SettingsTopBarMode.Root -> "root"
        }
}

// ───── Main Screen ─────
@Composable
internal fun TopBar(
    viewModel: SettingViewModel,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchRequested: () -> Unit,
    onSearchClosed: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackupRestoreClick: () -> Unit,
    isExportingBugReport: Boolean,
    onExportBugReportClick: () -> Unit,
    themeMode: ModuleThemeMode,
    monetEnabled: Boolean,
    onThemeModeChange: (ModuleThemeMode) -> Unit,
    onMonetEnabledChange: (Boolean) -> Unit,
    containerColor: Color,
) {
    val breadcrumbs by viewModel.breadcrumbs
    val topBarState = SettingsTopBarState(
        mode = when {
            isSearchActive -> SettingsTopBarMode.Search
            viewModel.isErrorOverviewActive -> SettingsTopBarMode.ErrorOverview
            !viewModel.isAtRoot -> SettingsTopBarMode.SubPage
            else -> SettingsTopBarMode.Root
        },
        path = viewModel.currentPath,
        breadcrumbs = breadcrumbs,
        searchQuery = searchQuery
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        AnimatedContent(
            targetState = topBarState,
            modifier = Modifier.fillMaxWidth(),
            contentKey = { it.animationKey },
            transitionSpec = {
                softFadeScaleIn(
                    initialScale = 0.98f,
                    durationMillis = TopBarTransitionDurationMillis
                ).togetherWith(
                    softFadeScaleOut(
                        targetScale = 0.98f,
                        durationMillis = TopBarTransitionDurationMillis
                    )
                )

            },
            label = "settings_top_bar_transition"
        ) { state ->
            when (state.mode) {
                SettingsTopBarMode.Search -> SearchBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onBackClick = onSearchClosed,
                )

                SettingsTopBarMode.ErrorOverview -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = MiuixIcons.Report,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "功能异常",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusPill(
                            text = "${viewModel.errorCount} 项",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onExportBugReportClick,
                            enabled = !isExportingBugReport,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Share,
                                contentDescription = "导出并分享异常报告",
                                tint = if (isExportingBugReport) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }

                SettingsTopBarMode.SubPage -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Scrollable so long breadcrumbs do not wrap/clip
                        val scrollState = rememberScrollState()
                        LaunchedEffect(state.breadcrumbs.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.breadcrumbs.forEachIndexed { index, crumb ->
                                if (index > 0) {
                                    Icon(
                                        imageVector = MiuixIcons.ChevronForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                Text(
                                    text = crumb.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (index == state.breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == state.breadcrumbs.lastIndex) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.navigateToBreadcrumb(crumb.fullPath) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                if (index < state.breadcrumbs.lastIndex) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }

                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsTopBarMode.Root -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = onSearchRequested) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onBackupRestoreClick) {
                            Icon(
                                imageVector = MiuixIcons.Backup,
                                contentDescription = "备份与清理",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        ThemeMenuButton(
                            themeMode = themeMode,
                            monetEnabled = monetEnabled,
                            onThemeModeChange = onThemeModeChange,
                            onMonetEnabledChange = onMonetEnabledChange,
                        )
                    }
                }
            }
        }
    }

}

@Composable
internal fun ThemeMenuButton(
    themeMode: ModuleThemeMode,
    monetEnabled: Boolean,
    onThemeModeChange: (ModuleThemeMode) -> Unit,
    onMonetEnabledChange: (Boolean) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = MiuixIcons.Theme,
                contentDescription = "主题",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OverlayListPopup(
            show = showMenu,
            alignment = PopupPositionProvider.Align.End,
            enableWindowDim = false,
            minWidth = 220.dp,
            onDismissRequest = { showMenu = false },
        ) {
            ListPopupColumn {
                ModuleThemeMode.entries.forEachIndexed { index, mode ->
                    val label = when (mode) {
                        ModuleThemeMode.System -> "跟随系统"
                        ModuleThemeMode.Light -> "浅色模式"
                        ModuleThemeMode.Dark -> "深色模式"
                    }
                    DropdownImpl(
                        item = DropdownItem(text = label),
                        optionSize = ModuleThemeMode.entries.size + 1,
                        isSelected = themeMode == mode,
                        index = index,
                        onSelectedIndexChange = {
                            showMenu = false
                            onThemeModeChange(mode)
                        },
                    )
                }
                DropdownImpl(
                    item = DropdownItem(
                        text = "Monet 动态色",
                    ),
                    optionSize = ModuleThemeMode.entries.size + 1,
                    isSelected = monetEnabled,
                    index = ModuleThemeMode.entries.size,
                    onSelectedIndexChange = {
                        showMenu = false
                        onMonetEnabledChange(!monetEnabled)
                    },
                )
            }
        }
    }
}

// ───── Category Card ─────
