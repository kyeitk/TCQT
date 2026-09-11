package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.reflect.FieldUtils
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole

@RegisterAction
object SortAtList : Feature(
    key = "sort_at_list",
    name = "优化排序@列表",
    desc = "键入'@'时重新排序成员列表，由群主·管理员·机器人·至普通群成员。",
) {


    override fun install() {
        loadOrThrow("com.tencent.mobileqq.aio.input.at.common.SubmitListEvent")
            .hookMethodAfter({
                name = "getItemList"
            }) { param ->
                val list = param.result as? List<Any?> ?: return@hookMethodAfter

                param.result = list.sortedWith(compareBy { item ->
                    rank(extractMemberInfo(item))
                })
            }
    }

    private fun extractMemberInfo(item: Any?): MemberInfo? {
        if (item == null) return null

        return FieldUtils.create(item)
            .typed<MemberInfo>()
            .preferInstance(true)
            .index(0)
            .getOrNull() as? MemberInfo
    }

    private fun rank(info: MemberInfo?): Int {
        if (info == null) return 0
        if (info.isRobot) return 3

        return when (info.role) {
            MemberRole.OWNER -> 1
            MemberRole.ADMIN -> 2
            MemberRole.MEMBER -> 4
            else -> 5
        }
    }
}
