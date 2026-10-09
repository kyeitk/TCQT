package com.owo233.tcqt.features.debug

import android.app.Activity
import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import java.util.Collections
import java.util.WeakHashMap

/**
 * 调试取证：输出 QQ 原生侧滑栏（SettingMe）的部件字段、菜单列表与真实 ViewTree。
 *
 * 只读不写，默认关闭。输出同时进入 logcat 与 TCQT 默认日志文件。
 */
@RegisterAction
object DumpSettingMeViewTree : Feature(
    key = "dump_setting_me_view_tree",
    name = "输出侧滑栏ViewTree",
    desc = "调试用途：输出QQ原生侧滑栏SettingMe的部件字段、菜单列表与真实ViewTree。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
) {

    private const val TAG = "DumpSettingMeViewTree"
    private const val SETTING_ME_PART = "com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3"
    private const val LAUNCHER_ACTIVITY = "com.tencent.mobileqq.activity.SplashActivity"
    private const val MAX_NODES_PER_DUMP = 600

    private val methodClassNames = listOf(
        SETTING_ME_PART,
        "com.tencent.mobileqq.settingme.QQSettingMeBackgroundView",
        "com.tencent.mobileqq.parts.aiavatar.b",
        "com.tencent.mobileqq.adapter.u",
        "com.tencent.mobileqq.adapter.t",
        "com.tencent.mobileqq.widget.QQSettingMeFloatingViewV3",
    )
    private const val MAX_DEPTH = 40

    private val knownRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val drawListeners = WeakHashMap<Activity, ViewTreeObserver.OnDrawListener>()
    private val dumpedActivities = WeakHashMap<Activity, Boolean>()

    override fun install() {
        hookPartInit()
        hookLauncherLifecycle()
        dumpClassMethods()
    }

    private fun hookPartInit() {
        val clazz = load(SETTING_ME_PART) ?: run {
            Log.w("$TAG $SETTING_ME_PART 不存在，侧滑栏取证跳过")
            return
        }
        runCatching {
            clazz.findMethod { name = "onInitView" }.hookAfter { param ->
                val part = param.thisObject ?: return@hookAfter
                runCatching { dumpPartMetadata(part) }
                    .onFailure { Log.w("$TAG 部件取证失败: $it") }
            }
        }.onFailure { Log.w("$TAG onInitView hook 安装失败: $it") }
    }

    private fun hookLauncherLifecycle() {
        Instrumentation::class.java
            .getMethod("callActivityOnResume", Activity::class.java)
            .hookAfter { param ->
                val activity = param.args.getOrNull(0) as? Activity ?: return@hookAfter
                if (activity.javaClass.name == LAUNCHER_ACTIVITY) {
                    attach(activity)
                }
            }
        Instrumentation::class.java
            .getMethod("callActivityOnDestroy", Activity::class.java)
            .hookAfter { param ->
                val activity = param.args.getOrNull(0) as? Activity ?: return@hookAfter
                detach(activity)
            }
    }

    private fun attach(activity: Activity) {
        if (drawListeners.containsKey(activity)) return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val listener = ViewTreeObserver.OnDrawListener { onDraw(activity, decor) }
        drawListeners[activity] = listener
        runCatching { decor.viewTreeObserver.addOnDrawListener(listener) }
    }

    private fun detach(activity: Activity) {
        val listener = drawListeners.remove(activity) ?: return
        val decor = activity.window.decorView as? ViewGroup ?: return
        runCatching { decor.viewTreeObserver.removeOnDrawListener(listener) }
        dumpedActivities.remove(activity)
    }

    private fun onDraw(activity: Activity, decor: ViewGroup) {
        val root = findSettingMeRoot(decor) ?: return
        if (root.visibility != View.VISIBLE || root.width <= 0 || root.height <= 0) return
        if (dumpedActivities[activity] == true) {
            removeAfterDraw(activity, decor)
            return
        }
        dumpedActivities[activity] = true
        nodeBudget = MAX_NODES_PER_DUMP
        Log.i("$TAG ===== SettingMe ViewTree root=${root.javaClass.name} w=${root.width} h=${root.height} =====")
        dumpViewTree(root, 0)
        scheduleDelayedDumps(root)
        removeAfterDraw(activity, decor)
    }

    private fun removeAfterDraw(activity: Activity, decor: ViewGroup) {
        val listener = drawListeners.remove(activity) ?: return
        decor.post { runCatching { decor.viewTreeObserver.removeOnDrawListener(listener) } }
    }

    private fun scheduleDelayedDumps(root: View) {
        root.postDelayed({ dumpLoadedUserTab(root, 1) }, 1500L)
        root.postDelayed({ dumpLoadedUserTab(root, 2) }, 4000L)
        root.postDelayed({ dumpLoadedUserTab(root, 3) }, 7000L)
    }

    private fun dumpLoadedUserTab(root: View, attempt: Int) {
        val pou = findView(root) { view ->
            view.id != View.NO_ID &&
                    runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() == "pou"
        } ?: return
        val pager = findView(pou) { view ->
            view.javaClass.name.contains("ViewPager2")
        } ?: return
        val pagerChild = (pager as? ViewGroup)
            ?.takeIf { it.childCount > 0 }
            ?.getChildAt(0) as? ViewGroup
        val pageLoaded = pagerChild?.childCount?.let { it > 0 } == true
        if (!pageLoaded && attempt < 3) return

        nodeBudget = MAX_NODES_PER_DUMP
        Log.i("$TAG ===== SettingMe 用户Tab loaded attempt=$attempt pageLoaded=$pageLoaded =====")
        dumpViewTree(pou, 0)
        dumpTextsUnder(pou)
    }

    private fun dumpTextsUnder(root: View) {
        var index = 0
        fun walk(view: View) {
            if (index++ >= 240) return
            val text = (view as? TextView)?.text?.toString()?.trim()
            val desc = view.contentDescription?.toString()?.trim()
            val drawable = (view as? ImageView)?.drawable?.javaClass?.name
            if (!text.isNullOrBlank() || !desc.isNullOrBlank() || drawable != null) {
                Log.i(
                    "$TAG [UI] class=${view.javaClass.name} id=0x${view.id.toString(16)} " +
                            "res=${resourceName(view)} text=$text desc=$desc drawable=$drawable"
                )
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
    }

    private fun dumpClassMethods() {
        methodClassNames.forEach { name ->
            val clazz = loadHostClass(name) ?: run {
                Log.w("$TAG class 未加载: $name")
                return@forEach
            }
            Log.i("$TAG ===== Class $name =====")
            clazz.declaredMethods
                .sortedBy { it.name }
                .forEachIndexed { index, method ->
                    if (index >= 120) return@forEachIndexed
                    val params = method.parameterTypes.joinToString(",") { it.name }
                    Log.i("$TAG method $name.${method.name}($params): ${method.returnType.name}")
                }
            Log.i("$TAG ===== Class $name fields =====")
            clazz.declaredFields
                .sortedBy { it.name }
                .forEachIndexed { index, field ->
                    if (index >= 80) return@forEachIndexed
                    Log.i("$TAG field ${field.name}: ${field.type.name}")
                }
        }
    }

    private fun loadHostClass(name: String): Class<*>? =
        load(name) ?: runCatching { HookEnv.hostClassLoader.loadClass(name) }.getOrNull()
    private fun dumpPartMetadata(part: Any) {
        val clazz = part.javaClass
        Log.i("$TAG ===== SettingMe Part 字段 =====")
        Log.i("$TAG part=${clazz.name}@${System.identityHashCode(part)}")

        var fieldCount = 0
        for (cls in generateSequence(clazz) { it.superclass }.takeWhile { it != Any::class.java }) {
            for (field in cls.declaredFields) {
                if (fieldCount++ >= 80) return
                val value = runCatching {
                    field.isAccessible = true
                    field.get(part)
                }.getOrNull()
                if (value is View) rememberRoot(value)
                Log.i(
                    "$TAG field ${cls.simpleName}.${field.name}: ${field.type.name}=" +
                            valueSummary(value)
                )
            }
        }

        val listFields = generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { cls -> cls.declaredFields.asSequence() }
            .filter { it.type == ArrayList::class.java }
            .toList()
        listFields.forEach { field ->
            runCatching {
                field.isAccessible = true
                val list = field.get(part) as? List<*> ?: return@forEach
                dumpList(field.name, list)
            }
        }

        dumpAdapter(clazz, part)
    }

    private fun dumpList(fieldName: String, list: List<*>) {
        Log.i("$TAG ===== SettingMe List field=$fieldName size=${list.size} =====")
        list.forEachIndexed { index, item ->
            val itemClass = item?.javaClass?.name ?: "null"
            val bean = runCatching {
                item?.javaClass?.declaredFields
                    ?.firstOrNull { it.name == "a" }
                    ?.apply { isAccessible = true }
                    ?.get(item)
            }.getOrNull()
            Log.i(
                "$TAG list[$index] item=$itemClass@${System.identityHashCode(item)} " +
                        "bean=${beanSimpleFields(bean)}"
            )
        }
    }

    private fun dumpAdapter(clazz: Class<*>, part: Any) {
        val adapterField = generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { cls -> cls.declaredFields.asSequence() }
            .firstOrNull { it.type.name.contains("adapter", ignoreCase = true) }
            ?: return
        val adapter = runCatching {
            adapterField.isAccessible = true
            adapterField.get(part)
        }.getOrNull() ?: return
        val count = runCatching {
            adapter.javaClass.methods
                .firstOrNull { it.name == "getItemCount" && it.parameterCount == 0 }
                ?.invoke(adapter)
        }.getOrNull()
        Log.i("$TAG adapter=${adapter.javaClass.name}@${System.identityHashCode(adapter)} count=$count")
    }

    private fun beanSimpleFields(bean: Any?): String {
        if (bean == null) return "null"
        val values = bean.javaClass.declaredFields
            .filter {
                it.type == String::class.java ||
                        it.type.isPrimitive ||
                        CharSequence::class.java.isAssignableFrom(it.type)
            }
            .mapNotNull { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(bean)
                    val text = when (value) {
                        is CharSequence -> "\"${value.take(40)}\""
                        else -> value?.toString()
                    }
                    "${field.name}=$text"
                }.getOrNull()
            }
        return values.joinToString(" ")
    }

    private fun valueSummary(value: Any?): String = when (value) {
        null -> "null"
        is CharSequence -> "\"${value.take(80)}\""
        is Number, is Boolean, is Character -> value.toString()
        is View ->
            "View(${value.javaClass.name}@${System.identityHashCode(value)} " +
                    "vis=${value.visibility} w=${value.width} h=${value.height})"
        is List<*> -> "List(size=${value.size})"
        is Map<*, *> -> "Map(size=${value.size})"
        else -> value.javaClass.name
    }

    private fun rememberRoot(view: View) {
        var node: View = view
        var parent = view.parent as? ViewGroup
        var up = 0
        while (parent != null && up < 12) {
            node = parent
            parent = node.parent as? ViewGroup
            up++
        }
        knownRoots += node
    }

    private fun findSettingMeRoot(decor: ViewGroup): View? {
        synchronized(knownRoots) {
            knownRoots.firstOrNull {
                it.isAttachedToWindow &&
                        it.visibility == View.VISIBLE &&
                        it.width > 0 &&
                        it.height > 0
            }?.let { return it }
        }
        return findView(decor) { view ->
            view is ViewGroup &&
                    view.childCount > 0 &&
                    view.javaClass.simpleName.contains("SettingMe", ignoreCase = true)
        }
    }

    private fun findView(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun dumpViewTree(view: View, depth: Int) {
        if (depth > MAX_DEPTH || nodeBudget <= 0) return
        nodeBudget--
        val group = view as? ViewGroup
        Log.i(
            "$TAG [TREE] ${"  ".repeat(depth)}depth=$depth " +
                    "class=${view.javaClass.name} " +
                    "id=0x${view.id.toString(16)} res=${resourceName(view)} " +
                    "vis=${view.visibility} alpha=${view.alpha} " +
                    "bounds=[${view.left},${view.top}-${view.right},${view.bottom}] " +
                    "x=${view.x} y=${view.y} w=${view.width} h=${view.height} " +
                    "tx=${view.translationX} ty=${view.translationY} " +
                    "sx=${view.scrollX} sy=${view.scrollY} " +
                    "click=${view.isClickable} focus=${view.isFocusable} enabled=${view.isEnabled} " +
                    "child=${group?.childCount ?: 0} tag=${tagSummary(view.tag)} " +
                    "desc=${view.contentDescription} " +
                    "text=${textOf(view)} drawable=${drawableOf(view)} adapter=${adapterOf(view)}"
        )
        if (group != null) {
            for (i in 0 until group.childCount) {
                dumpViewTree(group.getChildAt(i), depth + 1)
            }
        }
    }

    private fun resourceName(view: View): String {
        if (view.id == View.NO_ID) return "-"
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ?: "-"
    }

    private fun tagSummary(tag: Any?): String = when (tag) {
        null -> "null"
        is CharSequence -> "\"${tag.take(40)}\""
        is Number, is Boolean -> tag.toString()
        else -> tag.javaClass.name
    }

    private fun textOf(view: View): String =
        (view as? TextView)?.text?.toString()?.take(40).orEmpty()

    private fun drawableOf(view: View): String =
        (view as? ImageView)?.drawable?.javaClass?.name.orEmpty()

    private fun adapterOf(view: View): String = runCatching {
        val method = view.javaClass.methods.firstOrNull {
            it.name == "getAdapter" && it.parameterCount == 0
        } ?: return ""
        method.invoke(view)?.javaClass?.name.orEmpty()
    }.getOrNull().orEmpty()

    private var nodeBudget = MAX_NODES_PER_DUMP
}