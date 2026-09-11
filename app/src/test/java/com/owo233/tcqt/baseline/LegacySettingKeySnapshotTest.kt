package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 特征化快照：配置项的 key 与声明类型。
 *
 * 配置 key 有两种声明形式（见 S0 计划 §2 F4）：
 *  - **字面量**：`IntSetting("fake_pic_size.type", …)` —— 可精确断言；
 *  - **常量引用**：`MultiIntSetting(AntiRecallConfig.SETTING_KEY, …)` —— 字面量
 *    扫描看不见，本测试只断言"声明点数量没变"，常量求值留给 S1。
 *
 * 注意 `ext/Setting.kt`：它是抽象基类与五个子类的定义处，子类主构造函数写成
 * `class BooleanSetting(override val key: String, …)`，会被常量正则误匹配成
 * `BooleanSetting(override …)`。因此按 Kotlin 修饰符名单排除。
 */
class LegacySettingKeySnapshotTest {

    private val literalCtor = Regex(
        "(BooleanSetting|StringSetting|IntSetting|MultiIntSetting|IntSliderSetting)" +
                "\\s*\\(\\s*(?:key\\s*=\\s*)?\"([^\"]+)\""
    )

    private val constCtor = Regex(
        "(BooleanSetting|StringSetting|IntSetting|MultiIntSetting|IntSliderSetting)" +
                "\\s*\\(\\s*(?:key\\s*=\\s*)?([A-Za-z_][\\w.]*)"
    )

    private val kotlinModifiers = setOf(
        "override", "private", "public", "internal", "protected", "val", "var", "abstract"
    )

    /**
     * 只扫 `features/` 下的文件。
     *
     * 为什么限定范围：常量扫描的正则是 `(IntSetting|…)\s*\(\s*(?:key\s*=\s*)?([A-Za-z_][\w.]*)`，
     * 而 `api/Option.kt` 的 `toSetting()` 里写着 `BooleanSetting(key, name, …)`，
     * 会被当成"用常量 `key` 声明"。本测试的关心对象是**功能侧**的声明，
     * 因此把范围限定到 `features/`。
     */
    private fun featureFiles(): List<java.io.File> =
        SourceScanner.mainKotlinFiles().filter { it.path.replace('\\', '/').contains("/features/") }

    private fun literalDeclarations(): List<String> =
        featureFiles()
            .flatMap { f ->
                literalCtor.findAll(SourceScanner.stripComments(SourceScanner.read(f)))
                    .map { "${it.groupValues[2]}|${it.groupValues[1].removeSuffix("Setting")}" }
            }
            .sorted()

    private fun constDeclarationsByFile(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        featureFiles().forEach { f ->
            val count = constCtor
                .findAll(SourceScanner.stripComments(SourceScanner.read(f)))
                .count { it.groupValues[2] !in kotlinModifiers }
            if (count > 0) result[f.name] = count
        }
        return result
    }

    @Test
    fun `字面量配置 key 与基线一致`() {
        // 至此 features/ 下**不再有任何手写的配置 key 字面量**：89 个功能的 key
        // 全部由 Feature.key + Option.settingKey 派生，UnitedConfigHook 也已改写。
        // 历史值没有丢 —— 它们全部搬到了 DerivedKeyCompatibilityTest 的 EXPECTED 表里
        // 逐项钉住。
        val expected = emptyList<String>()

        assertEquals(expected, literalDeclarations(), "字面量配置 key 或声明类型发生漂移")
    }

    @Test
    fun `常量引用声明点数量与基线一致`() {
        // 90 个已注册功能全部迁移到 api 门面后，features/ 下不再有以常量引用
        // 声明配置 key 的地方（UnitedConfigHook 未注册，且它用的是字面量）。
        val expected = emptyMap<String, Int>()
        val actual = constDeclarationsByFile()

        assertEquals(expected, actual, "常量引用声明点发生漂移；S1 迁移时需逐个求值")
        assertEquals(0, actual.values.sum(), "常量引用声明点总数应为 0")
    }

    /**
     * 注释掉的 `@RegisterAction` 是"死代码"的典型形态。
     *
     * `UnitedConfigHook` 曾这样潜伏很久（基线 §4.1、交接文档 §6.5）：它既不会
     * 被注册，也没人删它，却仍在编译，还会让静态扫描误判功能数量。
     *
     * 已按「退役动作」删除。本用例把它变成机械断言：**仓库里不允许存在被注释掉的
     * `@RegisterAction`** —— 要么注册它，要么删掉它。
     */
    @Test
    fun `不存在被注释掉的 RegisterAction`() {
        val offenders = SourceScanner.mainKotlinFiles()
            .filter { COMMENTED_REGISTER_ACTION.containsMatchIn(SourceScanner.read(it)) }
            .map { it.name }

        assertTrue(
            offenders.isEmpty(),
            "以下文件把 @RegisterAction 注释掉了 —— 它不会被注册，也不是活代码，" +
                    "请删除或恢复：$offenders",
        )
    }

    private companion object {
        private val COMMENTED_REGISTER_ACTION = Regex(
            """/\*\s*@RegisterAction\s*\*/|//\s*@RegisterAction"""
        )
    }
}
