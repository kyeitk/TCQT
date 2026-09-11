package com.owo233.tcqt.features.chat

import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.invoke
import com.owo233.tcqt.core.reflect.new
import com.owo233.tcqt.host.QQInterfaces
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File

@RegisterAction
object PicTypeEmoticon : Feature(
    key = "pic_type_emoticon",
    name = "以图片方式打开表情",
    desc = "可以保存一些不让保存的表情。",
), DexKitTask {

    private val type by multiIntOption(
        settingKey = "type",
        name = "额外选项",
        options = listOf("不处理商城表情类型"),
    )

    override fun install() {
        hookPicTypeEmoticon()

        if (!type.isFlagEnabled(FLAG_MARKET_FACE)) {
            hookMarketFace()
            hookPath()
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        METHOD_EMOTICON_A to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.utils")
            matcher {
                paramCount(1)
                paramTypes("com.tencent.qqnt.kernel.nativeinterface.PicElement")
                returnType("java.lang.String")
                invokeMethods {
                    add {
                        name = METHOD_ASSEMBLE_MOBILE_QQ_RICH_MEDIA_FILE_PATH
                    }
                }
            }
        },
        METHOD_EMOTICON_C to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.utils")
            matcher {
                paramCount(2)
                paramTypes("com.tencent.qqnt.kernel.nativeinterface.PicElement", "int")
                returnType("java.lang.String")
                invokeMethods {
                    add {
                        name = METHOD_ASSEMBLE_MOBILE_QQ_RICH_MEDIA_FILE_PATH
                    }
                }
            }
        }
    )

    private fun hookPicTypeEmoticon() {
        CLASS_RICH_MEDIA_BROWSER_API.toClass.findMethod {
            name = METHOD_ENTER_IMAGE_PREVIEW
            paramCount = 9
        }.hookBefore { param ->
            param.args[8] = false
        }
    }

    private fun hookPath() {
        requireMethod(METHOD_EMOTICON_A).hookBefore { param ->
            val picElement = param.args[0] as PicElement
            if (picElement.md5HexStr == PLACEHOLDER_MD5) {
                param.result = picElement.sourcePath
            }
        }

        requireMethod(METHOD_EMOTICON_C).hookBefore { param ->
            val picElement = param.args[0] as PicElement
            if (picElement.md5HexStr == PLACEHOLDER_MD5) {
                param.result = picElement.sourcePath
            }
        }
    }

    private fun hookMarketFace() {
        CLASS_AIO_MARKET_FACE_API.toClass.findMethod {
            name = METHOD_ENTER_MARKET_FACE_PREVIEW
        }.hookReplace { param ->
            val clickedView = param.args[0] as View
            val msgRecord = param.args[1] as MsgRecord

            val relativePath = msgRecord.elements[MSG_ELEMENT_INDEX].marketFaceElement.staticFacePath
            if (relativePath.isEmpty()) return@hookReplace param.invokeOriginal()

            val context = clickedView.context
            val absolutePath = File(
                File(
                    context.getExternalFilesDir(null)!!.parentFile,
                    QQ_DATA_DIR
                ), relativePath
            ).absolutePath

            val msgElement = MsgElement().apply {
                val file = File(absolutePath)
                setPicElement(PicElement().apply {
                    sourcePath = absolutePath
                    md5HexStr = PLACEHOLDER_MD5
                    fileName = file.name
                    fileSize = file.length()
                    picType = PIC_TYPE_MARKET
                    transferStatus = 0
                    progress = 0
                    invalidState = 0
                })
            }

            val elements = msgRecord.elements
            val aioMsgItem = AIOMsgItem(msgRecord)
            val appRuntime = QQInterfaces.appRuntime
            val api = CLASS_RICH_MEDIA_BROWSER_API.toClass.new()
            val oldMsgType = msgRecord.msgType
            val oldElements = ArrayList(elements)

            elements.clear()
            elements.add(msgElement)
            msgRecord.msgType = MSG_TYPE_PIC

            try {
                api.invoke(
                    METHOD_ENTER_IMAGE_PREVIEW,
                    appRuntime,
                    context,
                    clickedView,
                    aioMsgItem,
                    msgElement,
                    true,
                    null,
                    null,
                    false,
                    withSuper = false
                )
            } finally {
                elements.clear()
                elements.addAll(oldElements)
                msgRecord.msgType = oldMsgType
            }

            return@hookReplace null
        }
    }

    private const val FLAG_MARKET_FACE = 0
    private const val PLACEHOLDER_MD5 = "tcqt_market_face_md5_placeholder"
    private const val MSG_TYPE_PIC = 2
    private const val PIC_TYPE_MARKET = 1000

    private const val METHOD_EMOTICON_A = "emoticon_a"
    private const val METHOD_EMOTICON_C = "emoticon_c"

    private const val CLASS_RICH_MEDIA_BROWSER_API =
        "com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl"
    private const val CLASS_AIO_MARKET_FACE_API =
        "com.tencent.qqnt.aio.adapter.api.impl.AIOMarketFaceApiImpl"
    private const val METHOD_ENTER_IMAGE_PREVIEW = "enterImagePreview"
    private const val METHOD_ENTER_MARKET_FACE_PREVIEW = "enterMarketFacePreviewWithSource"
    private const val METHOD_ASSEMBLE_MOBILE_QQ_RICH_MEDIA_FILE_PATH =
        "assembleMobileQQRichMediaFilePath"

    private const val QQ_DATA_DIR = "Tencent/MobileQQ"
    private const val MSG_ELEMENT_INDEX = 0
}
