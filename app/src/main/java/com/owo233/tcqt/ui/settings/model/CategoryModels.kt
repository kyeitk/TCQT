package com.owo233.tcqt.ui.settings.model

import androidx.compose.runtime.Immutable

@Immutable
data class CategoryNode(
    val name: String,
    val fullPath: String,
    val depth: Int,
    val label: String,
    val uiOrder: Int,
    val featureKeys: List<String>,
    val children: List<CategoryNode>
)

// ───── Feature Models ─────
@Immutable
data class CategoryUiState(
    /** Path segment name at this level */
    val name: String,
    /** Full path from root, e.g. "高级/过检测" */
    val fullPath: String,
    /** 0 = root level, 1 = first sub-level, … */
    val depth: Int,
    /** Display label (main feature's label, else [name]) */
    val label: String,
    /** Sorting order */
    val uiOrder: Int,
    /** Feature keys directly under this category (leaf) */
    val featureKeys: List<String>,
    /** Sub-categories (non-leaf) */
    val children: List<CategoryUiState>,
    /** Leaf: has features, no sub-categories */
    val isLeaf: Boolean,
    /** Enabled features in this whole subtree */
    val enabledCount: Int,
    /** Total features in this whole subtree */
    val totalFeatureCount: Int
)

/**
 * One breadcrumb segment for the top navigation bar.
 */
@Immutable
data class BreadcrumbItem(
    /** Display name */
    val name: String,
    /** Path to navigate to when clicked */
    val fullPath: String
)

// ───── Restart Prompt ─────
enum class RestartPrompt(
    val title: String,
    val message: String,
    val dismissMessage: String
) {

    Save(
        title = "设置已保存",
        message = "是否现在重启宿主以应用修改？",
        dismissMessage = "设置已保存，重启后生效"
    ),
    Clear(
        title = "配置已清空",
        message = "是否现在重启宿主以应用默认配置？",
        dismissMessage = "配置已清空，重启后生效"
    ),
    Restore(
        title = "配置已还原",
        message = "是否现在重启宿主以应用还原的配置？",
        dismissMessage = "配置已还原，重启后生效"
    )
}
