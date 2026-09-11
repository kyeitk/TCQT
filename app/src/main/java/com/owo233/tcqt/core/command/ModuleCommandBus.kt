package com.owo233.tcqt.core.command

import android.content.Context
import android.content.Intent

/**
 * 向模块自身广播命令。
 *
 * 接收端在 `features/internal/ModuleCommand.kt`。
 */
object ModuleCommandBus {

    const val ACTION_MODULE_COMMAND: String = "com.owo233.tcqt.MODULE_COMMAND"

    const val CMD_RESTART: String = "restart"
    const val CMD_EXIT: String = "exit"
    const val CMD_CONFIG_CLEAR: String = "config_clear"

    fun sendCommand(ctx: Context, command: String) {
        Intent(ACTION_MODULE_COMMAND).apply {
            putExtra("cmd", command)
            setPackage(ctx.packageName)
        }.also {
            ctx.sendBroadcast(it)
        }
    }
}
