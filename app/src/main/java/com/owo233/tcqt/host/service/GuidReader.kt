package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.PlatformTools
import com.owo233.tcqt.core.env.toHexString
import com.owo233.tcqt.core.log.Log
import com.tencent.guild.api.util.IGuildUtilApi
import com.tencent.mobileqq.msf.sdk.MSFSharedPreUtils
import com.tencent.mobileqq.qroute.QRoute
import oicq.wlogin_sdk.tools.MD5

object GuidReader {

    fun getGuid(): String {
        return runCatching {
            MSFSharedPreUtils.getGuid()
                ?.takeIf { it.isNotEmpty() }
                ?: QRoute.api(IGuildUtilApi::class.java)
                    .guid
                    .toHexString()
                    .takeIf { it.isNotEmpty() }
        }.getOrNull()
            ?: getGuidByAndroidID()
    }

    private fun getGuidByAndroidID(): String {
        Log.w("getGuid 触发保底行为!")
        val aid = PlatformTools.getAndroidID() ?: return ""
        val mac = "02:00:00:00:00:00"
        return MD5.toMD5Byte(aid + mac).toHexString()
    }
}
