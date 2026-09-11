package com.owo233.tcqt.features.misc

import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
object FakePhone : Feature(
    key = "fake_phone",
    name = "伪装手机号码",
    desc = "伪装账号与安全设置页面中的手机号码。",
), DexKitTask {

    /** 派生 key = `fake_phone.string.phone`。 */
    private val phone by stringOption(
        settingKey = "string.phone",
        name = "phone",
        desc = "填写要伪装的手机号码，如 1145141919810",
    )

    private val fakePhone: String
        get() = phone.ifEmpty { "1145141919810" }

    override fun install() {
        requireMethod("fake_phone").hookBefore { param ->
            if (param.args[0] == 5) {
                (param.args[2])?.let { obj ->
                    val bundle = obj as Bundle
                    bundle.putString("phone", fakePhone)
                    param.args[2] = bundle
                }
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "fake_phone" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.app")
            matcher {
                paramTypes = listOf("int", "boolean", "java.lang.Object")
                usingEqStrings("status", "wording", "target_desc", "target_name")
            }
        }
    )
}
