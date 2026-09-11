package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.action.ActionSpec
import com.owo233.tcqt.core.action.FeatureCategories
import com.owo233.tcqt.generated.GeneratedActionList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **目录即分类**的一致性护栏。
 *
 * S4 之前，功能在设置界面里的分类是一个写在构造参数里的中文字符串
 * （`uiTab = "界面"`），而目录早就按分类组织好了 —— 两处维护，必然漂移，
 * 正是本仓库最初的痛点「目录树与设置界面分类脱节」。
 *
 * 现在唯一真相是 `features/<分类>/` 包路径，[FeatureCategories] 只是把包名翻译成
 * 标签的**一张表**。本测试守住这条不变量的四个缺口：
 *
 * 1. 新包忘了登记 → 会静默落到兜底「基础」（最难发现）；
 * 2. 分类表里有僵尸分类（没有任何成员）；
 * 3. 有人又把 `uiTab = "…"` 写回功能里（第二处真相复活）；
 * 4. 分类集合与设置界面实际用到的集合不一致。
 */
class CategoryConsistencyTest {

    private fun visible() = RegisteredAction.load().filterNot { it.hidden }

    private fun fqnOf(action: RegisteredAction) = "${action.packageName}.${action.simpleName}"

    @Test
    fun `可见功能的包必须都在分类表里登记`() {
        val offenders = visible()
            .filterNot { FeatureCategories.isKnown(fqnOf(it)) }
            .map { "${it.simpleName} (${it.packageName})" }

        assertTrue(
            offenders.isEmpty(),
            "以下功能所在的包没有登记分类，会静默落到兜底「${FeatureCategories.FALLBACK}」——" +
                    "请在 FeatureCategories.LABELS 里补一行：\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `分类表里不得有没有任何成员的僵尸分类`() {
        val used = visible().map { it.uiTab }.toSet()
        val zombies = FeatureCategories.ORDER.filterNot { it in used }

        assertTrue(
            zombies.isEmpty(),
            "以下分类在 FeatureCategories 里登记了，但没有任何功能归属它：" +
                    "$zombies —— 要么删掉这一行，要么把功能搬进对应目录",
        )
    }

    @Test
    fun `分类集合与设置界面实际用到的集合一致`() {
        val used = visible().map { it.uiTab }.toSet()

        assertEquals(
            FeatureCategories.ORDER.toSet(),
            used,
            "分类表与设置界面实际分类不一致",
        )
    }

    @Test
    fun `功能源码里不得再出现手写的 uiTab`() {
        val uiTabDecl = Regex("""\buiTab\s*=""")

        val offenders = SourceScanner.mainKotlinFiles()
            .filter { it.path.replace('\\', '/').contains("/com/owo233/tcqt/features/") }
            .filter { uiTabDecl.containsMatchIn(SourceScanner.stripComments(SourceScanner.read(it))) }
            .map { it.name }

        assertTrue(
            offenders.isEmpty(),
            "分类由包路径唯一决定，不得再有第二处声明（这正是 S4 要消除的漂移源）：\n" +
                    offenders.joinToString("\n"),
        )
    }

    /**
     * **两条独立派生路径的交叉验证。**
     *
     * 生产走 `ActionSpec.uiTab` → `javaClass.name` → 分类表；
     * 本仓库的测试走源码文本里的 `package` 声明 → 分类表。
     *
     * 两者必须一致。这条用例能抓到：
     * - 源码 `package` 与文件所在目录不符（S1 阶段真实踩过：6 个文件的包名与目录
     *   不一致，编译通过、守卫也没报，因为守卫按包分类）；
     * - `package` 声明解析错误；
     * - 生产路径被改成别的取法却没同步。
     */
    @Test
    fun `生产派生路径与源码 package 声明一致`() {
        val bySource = RegisteredAction.load().associate { it.simpleName to it.uiTab }

        val mismatches = GeneratedActionList.ACTIONS.mapNotNull { cls ->
            val action = runCatching {
                cls.getField("INSTANCE").get(null) as? ActionSpec
            }.getOrNull() ?: return@mapNotNull null

            val expected = bySource[cls.simpleName] ?: return@mapNotNull null
            if (action.uiTab == expected) {
                null
            } else {
                "${cls.simpleName}: 生产=${action.uiTab} 源码=${expected}"
            }
        }

        assertTrue(
            mismatches.isEmpty(),
            "两条分类派生路径不一致 —— 通常意味着源码包名与目录不符：\n" +
                    mismatches.joinToString("\n"),
        )
    }

    /**
     * 让上面几条断言**自证可证伪**：`isKnown` 必须真的能区分"登记过"与"没登记"。
     *
     * 否则若它恒返回 true，`可见功能的包必须都在分类表里登记` 永远不会红 ——
     * 一条永远绿的护栏等于没有护栏。
     */
    @Test
    fun `分类判定本身可证伪`() {
        assertTrue(
            FeatureCategories.isKnown("com.owo233.tcqt.features.chat.FakePicSize"),
            "已登记的包被判成未登记",
        )
        assertTrue(
            !FeatureCategories.isKnown("com.owo233.tcqt.features.brand_new_pkg.Thing"),
            "未登记的包被判成已登记 —— 那条护栏会永远绿，等于没有",
        )
        assertTrue(
            !FeatureCategories.isKnown("com.owo233.tcqt.core.action.ActionSpec"),
            "非 features 包不该被认成功能分类",
        )
    }
}
