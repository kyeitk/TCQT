package com.owo233.tcqt.baseline

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 功能契约的唯一性：**只有 `api.Feature` / `api.InfraTask` 是实现入口**。
 *
 * ## 背景：为什么 `ActionSpec` 没有被删掉
 *
 * S2b 把 89 个注册功能全部迁到 `api.Feature` 之后，收尾目标原本是"删掉
 * `core.action.ActionSpec`"。实施时发现它与 spec §3.1 的层次表冲突：
 *
 * - `core` 不得依赖 `api`，而 `core.action.ActionRegistry` 必须持有功能类型；
 * - `ui` 也不得依赖 `api`，而 `ui.settings.FeatureCatalog` 要读功能的
 *   `key/name/desc/uiTab/uiOrder/uiType/hidden/settings`。
 *
 * 也就是说，注册表与设置界面需要一个**层次上够得着**的功能模型，它只能在 `core`。
 * 因此 `ActionSpec` 的角色被重新定义为"注册表/UI 的内部管道契约"，而
 * `api.Feature` 是**作者唯一该实现的契约**。二者不是"两套契约并存"，而是
 * "一个内部模型 + 一个作者门面"。
 *
 * ## 本测试守什么
 *
 * 既然名字上分不清（`ActionSpec` 听起来仍像作者接口），就用机械断言把它钉死：
 * `features/` 与 `host/` 下**不允许**出现直接实现 `ActionSpec` 的类。
 * 功能作者只能继承 `Feature` / `InfraTask`，从而自动获得：
 * 派生 key、声明式 `Requires`、`install()` 约定、`PipelineDecorator` 能力。
 */
class SingleAuthoringContractTest {

    /**
     * 匹配 `class X : ActionSpec` / `object X : ActionSpec, Foo` 这类直接实现，
     * 但**不**匹配 `: Feature(` / `: InfraTask(` / 泛型参数位置。
     */
    private val directImplementation = Regex(
        "(?:class|object)\\s+(\\w+)[^\\n{]{0,400}?:[^\\n{]{0,200}?\\bIAction\\b"
    )

    private fun filesIn(vararg topPackages: String) =
        SourceScanner.mainKotlinFiles().filter { file ->
            val rel = file.path.replace('\\', '/')
            topPackages.any { rel.contains("/com/owo233/tcqt/$it/") }
        }

    @Test
    fun `features 与 host 下不得出现直接实现 ActionSpec 的类`() {
        val offenders = mutableListOf<String>()

        filesIn("features", "host").forEach { file ->
            val text = SourceScanner.stripComments(SourceScanner.read(file))
            directImplementation.findAll(text).forEach { match ->
                offenders += "${file.name}: ${match.groupValues[1]}"
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "功能必须继承 Feature / InfraTask，不能直接实现 ActionSpec —— " +
                    "直接实现会绕过派生 key、声明式 Requires 与 install() 约定：\n" +
                    offenders.joinToString("\n"),
        )
    }

    @Test
    fun `全部注册功能都声明为 Feature 或 InfraTask`() {
        val offenders = RegisteredAction.load()
            .filter { it.superClass != "Feature" && it.superClass != "InfraTask" }
            .map { "${it.simpleName} : ${it.superClass.ifEmpty { "<未解析>" }}" }

        assertTrue(
            offenders.isEmpty(),
            "注册功能的父类必须是 Feature 或 InfraTask：\n" + offenders.joinToString("\n"),
        )
    }

    /**
     * spec §3.6：「禁止 `features.*` 之间互相 import（`features/internal/pipeline/`
     * 只允许经 `Registry` 能力发现）」。
     *
     * 这条此前**只有文字规定、没有守卫** —— `ArchitectureGuardTest` 按顶层包分类，
     * `features → features` 天然同层放行。结果 `AIOViewUpdate` 直接 import 了
     * `features.chat.ShowMsgInfo` / `features.message.RecallHeaderTip` 等 6 处，
     * 使"新增一个装饰器必须去改管线"这个原始痛点一直存在。
     *
     * S3 已把这些 import 换成 `PipelineDecorators` 能力发现；本用例防止它们长回来。
     */
    @Test
    fun `管线包不得 import 兄弟 features 包`() {
        val pipelinePrefix = "app/src/main/java/com/owo233/tcqt/features/internal/pipeline/"
        val siblingImport = Regex(
            "import\\s+com\\.owo233\\.tcqt\\.features\\.(?!internal\\.pipeline)[\\w.]+"
        )

        val offenders = mutableListOf<String>()
        SourceScanner.mainKotlinFiles()
            .filter { it.path.replace('\\', '/').contains(pipelinePrefix) }
            .forEach { file ->
                siblingImport.findAll(SourceScanner.stripComments(SourceScanner.read(file)))
                    .forEach { offenders += "${file.name}: ${it.value}" }
            }

        assertTrue(
            offenders.isEmpty(),
            "管线只能经 Registry 能力发现装饰器，不得 import 兄弟 features 包（spec §3.6）：\n" +
                    offenders.joinToString("\n"),
        )
    }
}
