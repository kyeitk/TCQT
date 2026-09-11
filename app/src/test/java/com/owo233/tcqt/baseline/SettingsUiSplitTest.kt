package com.owo233.tcqt.baseline

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `ui/settings/` 拆分后的结构护栏（spec §5.2 / §5.4）。
 *
 * ## 为什么需要它
 *
 * 拆分 `SettingScreen.kt`（1896 行）时踩过一次真实事故：工具写好了新文件但
 * **没从源文件里删掉**原声明，于是同一个包里出现了两份同名声明，编译器报
 * `Conflicting overloads` 与一串 `Overload resolution ambiguity`。
 *
 * 那次是编译兜住的 —— 但**只对非 `private` 声明有效**：Kotlin 的 `private`
 * 顶层声明是**文件级**可见，两个文件各有一个同名 `private fun` 完全合法。
 * 所以"搬走了但没删干净"这件事，对 private 声明编译器**不会报错**，
 * 只会静默留下死代码。本测试补上这一问。
 *
 * ## 判据
 *
 * 1. spec §5.2 列的 7 个文件都必须存在；
 * 2. `ui/settings/` 下不得出现重复的**非 private** 顶层声明名；
 * 3. 拆分后 `SettingScreen.kt` 不得再包含已被搬走的那些 Composable。
 */
class SettingsUiSplitTest {

    private val settingsDir =
        File(SourceScanner.repoRoot, "app/src/main/java/com/owo233/tcqt/ui/settings")

    private fun settingsFiles(): List<File> =
        settingsDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * 匹配**非 private** 顶层声明 —— 修饰符可省（`data class Foo` 默认就是 public）。
     *
     * 早先的写法强制要求 `internal |public ` 前缀，于是 `data class SettingFeature`
     * 这种最常见的形态反而被漏掉 —— 一条会漏判的护栏等于没有护栏。
     *
     * 扩展函数要取接收者之后的真名（`fun String.foo()` 的名字是 `foo`），
     * 否则同一包里的两个 `String` 扩展会被误判成重名。
     */
    private val publicTopLevelDecl = Regex(
        """^(?!private )(?:internal |public )?(?:const )?""" +
                """(?:fun|val|var|class|data class|enum class|sealed class|object)\s+""" +
                """(?:[\w<>,?. ]+\.)?(\w+)""",
        RegexOption.MULTILINE
    )

    @Test
    fun `spec §5_2 列出的拆分文件都存在`() {
        val expected = listOf(
            "SettingScreen.kt",
            "SettingsTopBar.kt",
            "FeatureCard.kt",
            "CategoryCard.kt",
            "SearchBar.kt",
            "SearchHistoryLayout.kt",
            "FooterAndAbout.kt",
        )

        val missing = expected.filterNot { File(settingsDir, it).exists() }

        assertTrue(
            missing.isEmpty(),
            "spec §5.2 要求的拆分文件缺失：$missing",
        )
    }

    @Test
    fun `ui settings 下不得有重复的非 private 顶层声明`() {
        val byName = mutableMapOf<String, MutableList<String>>()

        settingsFiles().forEach { file ->
            publicTopLevelDecl.findAll(SourceScanner.stripComments(SourceScanner.read(file)))
                .forEach { m ->
                    byName.getOrPut(m.groupValues[1]) { mutableListOf() }.add(file.name)
                }
        }

        val duplicates = byName.filter { it.value.size > 1 }

        assertTrue(
            duplicates.isEmpty(),
            "同一包里出现重复的非 private 顶层声明（编译会报 Conflicting overloads；" +
                    "若是搬迁留下的死代码请删除）：\n" +
                    duplicates.entries.joinToString("\n") { "  ${it.key} <- ${it.value}" },
        )
    }

    @Test
    fun `SettingScreen_kt 不再包含已搬走的 Composable`() {
        val screen = SourceScanner.read(File(settingsDir, "SettingScreen.kt"))
        val movedAway = listOf(
            "SettingsTopBarMode", "SettingsTopBarState", "TopBar", "ThemeMenuButton",
            "CategoryCard", "CompactHeaderCard", "OverviewStat", "OverviewDivider",
            "ErrorOverviewHeader", "SearchPromptCard", "SearchHistoryLayout", "EmptyStateCard",
            "SearchBar", "SearchInputField", "FooterCard", "SavePill",
            "FeatureCard", "FeaturePreferenceDetails", "FeatureTextArea", "FeatureErrorPanel",
            "ForcedDisabledHint", "rememberHighlightedText", "StatusPill", "OptionGroup",
            "FeatureSlider",
        )

        val stale = movedAway.filter { name ->
            Regex(
                """^(?:internal |private )?(?:fun|val|class|data class|enum class)\s+(?:[\w<>,?. ]+\.)?${
                    Regex.escape(
                        name
                    )
                }\b""",
                RegexOption.MULTILINE
            ).containsMatchIn(SourceScanner.stripComments(screen))
        }

        assertTrue(
            stale.isEmpty(),
            "SettingScreen.kt 里仍留着已搬走的声明（搬迁没删干净）：$stale",
        )
    }
}
