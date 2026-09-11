package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.hook.MethodHookParam

interface MessageHandler {

    fun handleInfoSyncPush(buffer: ByteArray, param: MethodHookParam)
    fun handleMsgPush(buffer: ByteArray, param: MethodHookParam)
}
