package com.owo233.tcqt.host.service

import com.owo233.tcqt.core.env.loadOrThrow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 主页右上角加号菜单的「额外选项」注册表。
 *
 * 本类不是 Action：菜单 hook 由可见功能 `features/menu/AddPlusMenu` 在自己的
 * `install()` 里安装，条目也只在该功能启用时注册。
 */
object PlusMenuManager {

    private val items = CopyOnWriteArrayList<ExtraMenuItem>()

    fun register(item: ExtraMenuItem) {
        items.add(item)
    }

    fun registerAll(vararg menuItems: ExtraMenuItem) {
        items.addAll(menuItems)
    }

    /** 按 id 查找已注册条目，供点击回调派发。 */
    fun findById(id: Int): ExtraMenuItem? = items.find { it.id == id }

    /** 按 id 排序后构造宿主 `PopupMenuDialog$MenuItem` 实例。 */
    fun buildMenuItems(): List<Any> {
        val clazz = loadOrThrow($$"com.tencent.widget.PopupMenuDialog$MenuItem")
        return items.sortedBy { it.id }.map { item ->
            clazz.getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            ).newInstance(item.id, item.title, item.title, item.iconResId)
        }
    }
}

data class ExtraMenuItem(
    val id: Int,
    val title: String,
    val iconResId: Int,
    val onClick: () -> Unit
)
