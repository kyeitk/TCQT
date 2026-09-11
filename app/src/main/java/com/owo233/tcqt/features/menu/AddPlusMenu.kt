package com.owo233.tcqt.features.menu

import android.app.Activity
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.command.ModuleCommandBus
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.ResourcesUtils
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.host.service.ExtraMenuItem
import com.owo233.tcqt.host.service.PlusMenuManager
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
object AddPlusMenu : Feature(
    key = "add_plus_menu",
    name = "添加额外选项",
    desc = "给主页右上角菜单添加额外功能选项(结束/重启进程)。",
    /**
     * 主页加号菜单在首页初始化时就会构建，必须在构建前注册好菜单项，否则首次启动会漏。
     */
    priority = ActionPriority.EARLY,
), DexKitTask {

    override fun install() {
        PlusMenuManager.registerAll(
            ExtraMenuItem(
                id = 23331,
                title = "结束进程",
                iconResId = com.owo233.tcqt.R.drawable.ic_item_exit_72dp,
                onClick = { ModuleCommandBus.sendCommand(hostApp, ModuleCommandBus.CMD_EXIT) }
            ),
            ExtraMenuItem(
                id = 23332,
                title = "重启进程",
                iconResId = com.owo233.tcqt.R.drawable.ic_item_reboot_72dp,
                onClick = { ModuleCommandBus.sendCommand(hostApp, ModuleCommandBus.CMD_RESTART) }
            )
        )

        hookBuild()
        hookClick()
    }

    private fun hookBuild() {
        loadOrThrow("com.tencent.widget.PopupMenuDialog")
            .hookMethodBefore(
                "conversationPlusBuild",
                Activity::class.java,
                List::class.java,
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener"),
                loadOrThrow($$"com.tencent.widget.PopupMenuDialog$OnDismissListener")
            ) { param ->
                val activity = param.args[0] as Activity
                ResourcesUtils.injectResourcesToContext(activity.resources)
                param.args[1] = (param.args[1] as List<*>) + PlusMenuManager.buildMenuItems()
            }
    }

    private fun hookClick() {
        requireMethod("AddPlusMenu").hookBefore { param ->
            val clickedId = param.args[0]!!.getObject("id") as Int
            PlusMenuManager.findById(clickedId)?.let {
                it.onClick()
                param.result = Unit
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "AddPlusMenu" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.activity.recent")
            matcher {
                name = "onClickAction"
                paramTypes($$"com.tencent.widget.PopupMenuDialog$MenuItem")
                declaredClass {
                    addInterface($$"com.tencent.widget.PopupMenuDialog$OnClickActionListener")
                }
            }
        }
    )
}
