package com.owo233.tcqt.features.menu.settingmecards

import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mqq.app.MobileQQ
import java.io.File
import java.util.WeakHashMap

internal fun hostAccount(): String = runCatching {
    MobileQQ.getMobileQQ().peekAppRuntime()?.currentAccountUin.orEmpty()
}.getOrDefault("")

@Serializable
internal data class WalletSnapshot(val account: String, val amount: String, val updatedAt: Long,
    val sourceVersion: Int = 0)

/** Observe the native cash balance display: its text already has QQ's currency units. */
internal object WalletBalanceSource {
    private val owners = WeakHashMap<View, String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val amountPattern = Regex("(?:[¥￥]\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)")
    private var installed = false
    private var last: WalletSnapshot? = null

    fun install() {
        if (installed) return
        installed = true
        val clazz = load("com.tencent.mobileqq.qwallet.home.TopAppView") ?: run {
            Log.w("SettingMeCards 当前版本未找到原生钱包余额视图")
            return
        }
        clazz.declaredConstructors.forEach { constructor ->
            constructor.hookAfter { param ->
                val view = param.thisObject as? View ?: return@hookAfter
                synchronized(owners) { owners.putIfAbsent(view, hostAccount()) }
            }
        }
        clazz.declaredMethods.filter {
            it.name in setOf("setTypeAndAppInfo", "p1", "q1", "m1")
        }.forEach { method ->
            method.hookAfter { param ->
                val view = param.thisObject as? View ?: return@hookAfter
                view.post { capture(view) }
            }
        }
        Log.i("SettingMeCards 已安装原生钱包余额展示观察器")
    }

    private fun capture(root: View) {
        val account = synchronized(owners) { owners[root] }.orEmpty()
        if (account.isBlank() || account != hostAccount()) return
        val texts = mutableListOf<String>()
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            (view as? TextView)?.text?.toString()?.trim()?.let(texts::add)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        // Q coins, investment assets and limits must never be mistaken for cash balance.
        val amount = NativeBalanceText.parse(texts) ?: return
        val snapshot = WalletSnapshot(account, amount, System.currentTimeMillis(), sourceVersion = 1)
        val previous = last
        if (previous?.account == account && previous.amount == snapshot.amount &&
            snapshot.updatedAt - previous.updatedAt < 10_000L) return
        last = snapshot
        ModuleScope.launchIO("SettingMeCards.Wallet") {
            runCatching {
                val file = cacheFile(account)
                file.parentFile?.mkdirs()
                val atomic = AtomicFile(file)
                val stream = atomic.startWrite()
                try {
                    stream.write(json.encodeToString(WalletSnapshot.serializer(), snapshot).toByteArray())
                    atomic.finishWrite(stream)
                } catch (error: Throwable) {
                    atomic.failWrite(stream)
                    throw error
                }
            }.onFailure { Log.w("SettingMeCards 钱包余额缓存写入失败 (${it.javaClass.simpleName})") }
        }
    }

    fun read(account: String): WalletSnapshot? {
        if (account.isBlank()) return null
        return runCatching {
            val snapshot = json.decodeFromString(WalletSnapshot.serializer(),
                AtomicFile(cacheFile(account)).openRead().bufferedReader().use { it.readText() })
            snapshot.takeIf {
                it.sourceVersion == 1 && it.account == account && amountPattern.matches(it.amount) && it.updatedAt > 0
            }
        }.getOrNull()
    }

    private fun cacheFile(account: String): File {
        require(account.all(Char::isDigit))
        return File(HookEnv.moduleDataPath, "settingme-cards/wallet-$account.json")
    }
}
