package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 特征化快照：**历史**功能的分类归属（与 gate-keys 共用 `baseline/gate-keys.txt`，
 * uiTab 是其中一列）。
 *
 * ## 新增功能需要改这里吗？—— **不需要**
 *
 * 与 `LegacyGateKeySnapshotTest` 同理：这份 golden 是冻结的历史基线，守的是
 * 「**已发布功能的分类不许漂移**」（分类变了 = 用户找不到原来的开关），
 * 而不是「不许新增功能」。
 *
 * 早先这里是**精确相等**（`assertEquals(parseGolden(), actual)` 与精确分布），
 * 每加一个功能都要改两处断言 —— 已改为**子集语义**：历史条目必须仍在原分类，
 * 且每个历史分类的成员数不得减少；新增条目一律放行。
 *
 * 分类本身已由包路径唯一决定（S4「目录即分类」），因此新增功能只要放进
 * `features/<分类>/` 就自动归位，测试无需任何改动。
 */
class LegacyCategorySnapshotTest {

    private fun parseGolden(): Map<String, String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("baseline/gate-keys.txt")) {
            "缺少 app/src/test/resources/baseline/gate-keys.txt"
        }.bufferedReader(Charsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { line ->
                val parts = line.split('|')
                parts[0] to parts[2]
            }

    @Test
    fun `历史功能的分类归属不变`() {
        val actual = RegisteredAction.load()
            .filterNot { it.hidden }
            .associate { it.simpleName to it.uiTab }

        val problems = parseGolden().mapNotNull { (name, tab) ->
            // 历史功能"消失"由 LegacyGateKeySnapshotTest 负责报，这里只管分类漂移
            val now = actual[name] ?: return@mapNotNull null
            if (now == tab) null else "$name：$tab → $now"
        }

        assertTrue(
            problems.isEmpty(),
            "历史功能的分类漂移了（用户会找不到原来的开关）：\n" + problems.joinToString("\n"),
        )
    }

    @Test
    fun `历史分类的成员数不得减少`() {
        val actual = RegisteredAction.load()
            .filterNot { it.hidden }
            .groupingBy { it.uiTab }
            .eachCount()

        val golden = parseGolden().values.groupingBy { it }.eachCount()

        val decreased = golden.filter { (category, count) -> (actual[category] ?: 0) < count }

        assertTrue(
            decreased.isEmpty(),
            "以下分类的成员变少了 —— 通常是功能被搬错了目录：\n" +
                decreased.entries.joinToString("\n") {
                    "  ${it.key}：基线 ${it.value} → 现在 ${actual[it.key] ?: 0}"
                },
        )
    }

    @Test
    fun `当前不存在任何二级分类`() {
        val withSlash = RegisteredAction.load().filter { it.uiTab.contains('/') }
        assertEquals(
            emptyList(), withSlash.map { it.simpleName },
            "出现了二级分类 —— 若这是预期结果，请更新本测试与基线 §4.1"
        )
    }
}
