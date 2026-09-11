package com.owo233.tcqt.features.misc

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.widget.EditText
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object FilterSpecialCharacters : Feature(
    key = "filter_special_characters",
    name = "过滤聊天消息特殊字符",
    desc = "将聊天消息中出现的特殊字符替换为空格。",
) {


    override fun install() {
        TextView::class.java.findMethod {
            name = "onDraw"
            paramTypes(Canvas::class.java)
        }.hookBefore { param ->
            if (param.thisObject !is TextView) return@hookBefore
            if (param.thisObject is EditText) return@hookBefore

            val str = (param.thisObject as TextView).text.toString()
            if (BLACKLIST.none { ch -> str.contains(ch) }) return@hookBefore
            (param.thisObject as TextView).text = filterControlCharacter(str)
        }
    }

    private fun filterControlCharacter(str: CharSequence): CharSequence {
        var ret = str.toString()
        BLACKLIST.forEach { ret = ret.replace(it, ' ') }
        return ret
    }


    @SuppressLint("BidiSpoofing")
    private const val BLACKLIST = "‭‮‪‫‎⁦⁧‏"
}
