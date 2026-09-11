package com.owo233.tcqt.baseline

import com.owo233.tcqt.core.action.FeatureCategories
import java.io.File

/**
 * `GeneratedActionList.kt` 的解析结果 —— 本仓库注册项的唯一权威来源。
 *
 * 不要改用"扫描源码里的 @RegisterAction 注解"：那会把被注释掉的注解
 * （UnitedConfigHook）与完全没有注解的类（RecallHeaderTip）算进来。
 * 基线 rev0 正是这样把 90/83 错报成 91/87。
 */
internal data class RegisteredAction(
    val fqn: String,
    val simpleName: String,
    val sourceFile: File,
    val source: String,
) {
    /**
     * 覆写了 hidden 就用覆写值；否则继承两个基础设施基类的 hidden = true。
     *
     * `InfraTask`（S2b 引入）把 `hidden = true` 写在基类里，所以覆写正则看不到它 ——
     * 只能靠父类名判断，与 `AlwaysRunAction` 同理。
     */
    val hidden: Boolean by lazy {
        val override = HIDDEN_OVERRIDE.find(source)?.groupValues?.get(1)
        if (override != null) override == "true"
        else superClass == "AlwaysRunAction" || superClass == "InfraTask"
    }

    val superClass: String by lazy {
        Regex("(?:class|object)\\s+${Regex.escape(simpleName)}\\s*:\\s*([\\w.]+)")
            .find(source)
            ?.groupValues?.get(1)
            ?.substringAfterLast('.')
            .orEmpty()
    }

    val key: String by lazy {
        KEY_DECL.find(source)?.groupValues?.get(1)
            ?: KEY_CTOR_ARG.find(source)?.groupValues?.get(1).orEmpty()
    }

    /**
     * 分类**由包路径派生**（目录即分类）。
     *
     * 之前这里读的是源码里的 `uiTab = "..."` 字符串，而那是**第二处真相** ——
     * 目录早就按分类组织好了，设置界面却读字符串，两者必然漂移（本仓库最初的
     * 痛点之一）。S4 把 83 个字符串删掉后，唯一真相是源码的 `package` 声明，
     * [FeatureCategories] 只是包名 → 标签的翻译表。
     *
     * 刻意**不复用生产属性**（`ActionSpec.uiTab`）：这里从源码文本里解析
     * `package`，与生产走 `javaClass.name` 是两条独立路径，互为交叉验证。
     */
    val uiTab: String by lazy {
        FeatureCategories.labelOf("$packageName.$simpleName") ?: FeatureCategories.FALLBACK
    }

    /** 源码里的 `package` 声明。 */
    val packageName: String by lazy {
        PACKAGE_DECL.find(source)?.groupValues?.get(1).orEmpty()
    }

    val uiType: String by lazy {
        UI_TYPE.find(source)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: "SWITCH"
    }

    val relativePath: String
        get() = sourceFile.path.replace('\\', '/').substringAfter("app/src/main/java/")

    companion object {
        private val HIDDEN_OVERRIDE = Regex(
            "override\\s+val\\s+hidden[^=]{0,60}?=\\s*(\\w+)", RegexOption.DOT_MATCHES_ALL
        )
        private val UI_TYPE = Regex(
            "uiType[^=]{0,60}?=\\s*ActionUiType\\.(\\w+)", RegexOption.DOT_MATCHES_ALL
        )
        private val PACKAGE_DECL = Regex(
            "^\\s*package\\s+([\\w.]+)", RegexOption.MULTILINE
        )
        private val KEY_DECL = Regex(
            "override\\s+val\\s+key\\s*(?::\\s*String)?\\s*(?:get\\(\\)\\s*)?=\\s*\"([^\"]*)\"",
            RegexOption.DOT_MATCHES_ALL
        )

        /**
         * 新契约（`com.owo233.tcqt.api.Feature`）把 key 作为**构造参数**声明：
         * `Feature(key = "fake_pic_size", …)`。限定在 `Feature(` 调用内，
         * 避免误匹配功能体里其它 `key = "…"`。
         *
         * `InfraTask` 是 `Feature` 的抽象子类，构造参数写法完全一致，
         * 因此两个构造器名都要匹配 —— 漏掉 `InfraTask` 会让那 7 个功能的
         * key 解析成空串，进而被当成"多出来的可见功能"。
         */
        private val KEY_CTOR_ARG = Regex(
            "(?:Feature|InfraTask)\\s*\\([\\s\\S]{0,400}?\\bkey\\s*=\\s*\"([^\"]*)\""
        )

        fun load(): List<RegisteredAction> {
            val text = SourceScanner.read(SourceScanner.generatedActionList)
            val allFiles = SourceScanner.mainKotlinFiles()
            return Regex("com\\.owo233\\.tcqt\\.[\\w.]+(?=::class\\.java)")
                .findAll(text)
                .map { it.value }
                .distinct()
                .map { fqn ->
                    val simple = fqn.substringAfterLast('.')
                    val file = allFiles.firstOrNull { it.name == "$simple.kt" }
                        ?: error("注册类 $fqn 找不到源文件 $simple.kt")
                    RegisteredAction(
                        fqn = fqn,
                        simpleName = simple,
                        sourceFile = file,
                        source = SourceScanner.read(file),
                    )
                }
                .toList()
        }
    }
}
