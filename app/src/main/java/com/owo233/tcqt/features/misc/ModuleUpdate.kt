package com.owo233.tcqt.features.misc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.env.TCQTBuild
import com.owo233.tcqt.core.hook.HookEngineManager
import com.owo233.tcqt.core.hook.HookFramework
import com.owo233.tcqt.core.sync.ReceiverRegistry

@RegisterAction
object ModuleUpdate : Feature(
    key = "module_update",
    name = "模块更新干掉宿主",
    desc = "每次本模块更新后将自动重启（杀死）宿主进程。",
    defaultEnabledProvider = {
        HookEngineManager.engine.frameworkName != HookFramework.ZYGISK &&
                HookEngineManager.engine.apiLevel < 102
    },
    requires = Requires(nonZygiskOnly = true),
) {

    override fun install() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED -> {
                        val packageName = intent.data?.schemeSpecificPart
                        if (packageName == TCQTBuild.APP_ID) {
                            ModuleCommandBus.sendCommand(hostApp, ModuleCommandBus.CMD_RESTART)
                        }
                    }
                }
            }
        }

        ReceiverRegistry.register(hostApp, receiver, filter, exported = true)
    }
}
