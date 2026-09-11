package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.emptyParam
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.isPublic

@RegisterAction
object RemoveMediaLimit : Feature(
    key = "remove_media_limit",
    name = "移除媒体选择数量限制",
    desc = "移除聊天页相册最多只能选择20张图片/视频的限制，移除空间上传最多只能选择50张图片/视频的限制。",
) {


    override fun install() {
        // 群聊私聊
        loadOrThrow(
            "com.tencent.qqnt.qbasealbum.select.viewmodel.SelectedMediaViewModel"
        )
            .declaredMethods
            .single { method ->
                method.isPublic && method.emptyParam && method.returnType == Boolean::class.java
            }.hookBefore { param ->
                param.result = true
            }

        // 空间相册选择器移除数量限制
        loadOrThrow(
            "com.tencent.mobileqq.wink.picker.core.viewmodel.WinkSelectedMediaViewModel"
        )
            .declaredMethods
            .filter { method -> // 有两个符合条件的方法，都 hook
                method.isPublic && method.emptyParam && method.returnType == Boolean::class.java
            }.forEach { method ->
                method.hookBefore { param ->
                    param.result = true
                }
            }

        // 移除下一步点击限制
        loadOrThrow(
            "com.tencent.mobileqq.wink.picker.qzone.viewmodel.QZoneSelectedMediaViewModel"
        )
            .getMethod("getCurSelectedSize")
            .hookBefore { param ->
                param.result = 1
            }

        // 移除上传配置活动数量限制
        loadOrThrow("common.config.service.QzoneConfig")
            .getMethod(
                "getConfig",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            .hookBefore { param ->
                val key1 = param.args[0] as String
                val key2 = param.args[1] as String
                if (key1 == "PublishMood" && key2 == "MoodPhotoMaxNum") {
                    param.result = 114514
                }
            }
    }

}
