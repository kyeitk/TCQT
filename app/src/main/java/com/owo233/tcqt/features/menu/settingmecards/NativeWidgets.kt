package com.owo233.tcqt.features.menu.settingmecards

import android.content.Context
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import android.graphics.Typeface
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.features.menu.MiuixIconRenderer
import com.owo233.tcqt.features.menu.SettingMeUiConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Only the added widgets are rendered here; QQ continues to render all original columns. */
internal class NativeWidgets(
    context: Context,
    private val config: SettingMeUiConfig,
    private val bridge: NativeCardBridge,
) : LinearLayout(context) {
    private val dark get() = HookEnv.isNightMode()
    private val colors get() = NativeMiuixStyle.colors(dark)
    private val foreground get() = colors.onSurfaceContainer.toArgb()
    private val secondary get() = colors.onSurfaceVariantSummary.toArgb()
    private val accent get() = colors.primary.toArgb()
    private var renderedNight = dark
    private var layout = CardLayoutCodec.decode(config.readCardLayout())
    private var weather: CardWeatherSnapshot? = null
    private var weatherFailed = false
    private var weatherJob: Job? = null
    private var walletJob: Job? = null
    private var wallet: WalletSnapshot? = null
    private var account = ""
    private var hidden = false
    private var weatherValue: TextView? = null
    private var weatherDetails: TextView? = null
    private var weatherCondition: TextView? = null
    private var weatherHighLow: TextView? = null
    private var weatherHumidity: TextView? = null
    private var weatherWind: TextView? = null
    private var weatherUpdated: TextView? = null
    private var walletValue: TextView? = null
    private var walletUpdated: TextView? = null
    private var clockValue: TextView? = null
    private var clockDate: TextView? = null
    private var clockGreeting: TextView? = null
    private var clockMinute = Long.MIN_VALUE
    private var clockFormatKey = ""
    private val visibleRect = Rect()
    private val tick = object : Runnable {
        override fun run() {
            if (isShown && windowVisibility == View.VISIBLE && getGlobalVisibleRect(visibleRect)) {
                if (renderedNight != dark) render()
                updateClock()
                val current = hostAccount()
                if (current != account) { account = current; wallet = null; hidden = false; updateWallet() }
                if (walletJob?.isActive != true) walletJob = ModuleScope.launchIO("SettingMeWidgets.Wallet") {
                    val next = WalletBalanceSource.read(current)
                    post { if (current == account && current == hostAccount()) { wallet = next; updateWallet() } }
                }
            }
            postDelayed(this, 1000L)
        }
    }

    init { orientation = VERTICAL; tag = NativeCardBridge.CARD_PANEL_TAG; render() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val currentLayout = CardLayoutCodec.decode(config.readCardLayout())
        if (layout != currentLayout) { layout = currentLayout; render() }
        post(tick)
        refreshWeather(force = false)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        weatherJob?.cancel(); walletJob?.cancel()
        super.onDetachedFromWindow()
    }

    private fun render() {
        renderedNight = dark
        removeAllViews()
        weatherValue = null; weatherDetails = null; weatherUpdated = null
        weatherCondition = null; weatherHighLow = null; weatherHumidity = null; weatherWind = null
        walletValue = null; walletUpdated = null
        clockValue = null; clockDate = null; clockGreeting = null
        clockMinute = Long.MIN_VALUE
        val heading = row()
        heading.setPadding(dp(WidgetInset), 0, dp(WidgetInset), 0)
        heading.addView(text("小组件", 14, secondary).apply { setTypeface(typeface, Typeface.BOLD) }, LayoutParams(0, dp(44), 1f))
        heading.addView(icon(MiuixIcons.Edit, "编辑小组件") { edit() })
        addView(heading)
        layout.cards.filter { it.enabled }.forEach { spec ->
            when (spec.kind) {
                CardKind.WEATHER -> addView(weatherCard(spec))
                CardKind.WALLET -> addView(walletCard(spec))
                CardKind.CLOCK -> addView(clockCard(spec))
                else -> Unit // Optional native columns remain QQ's own content.
            }
        }
        updateWeather(); updateWallet(); updateClock()
    }

    private fun weatherCard(spec: CardSpec): LinearLayout {
        val card = card(spec.compact)
        val heading = row()
        heading.addView(cardTitle(config.weatherCity.substringBefore(','), MiuixIcons.Location), LayoutParams(0, -2, 1f))
        heading.addView(icon(MiuixIcons.Refresh, "刷新天气", alignVisibleEnd = true) { refreshWeather(true) })
        card.addView(heading)
        val temperature = column()
        val value = text("读取天气中", 20, foreground).apply { textDirection = View.TEXT_DIRECTION_LTR }
        weatherValue = value
        temperature.addView(value)
        weatherDetails = text("", NativeMiuixStyle.Body, secondary)
        temperature.addView(weatherDetails, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val condition = row()
        condition.addView(ImageView(context).apply {
            setImageDrawable(MiuixIconRenderer.toDrawable(context, MiuixIcons.CloudFill, dp(22)))
            setColorFilter(secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(22), dp(22)))
        weatherCondition = text("", 14, secondary)
        condition.addView(weatherCondition, LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        temperature.addView(condition, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val metrics = column()
        val metricTexts = mutableListOf<Pair<TextView, TextView>>()
        fun metric(label: String): TextView {
            val line = row()
            val name = text(label, NativeMiuixStyle.Caption, secondary)
            val amount = text("—", if (spec.compact) 13 else 14, foreground).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                textDirection = View.TEXT_DIRECTION_LTR
            }
            line.addView(name, LayoutParams(0, -2, 1f))
            line.addView(amount, LayoutParams(-2, -2).apply { marginStart = dp(8) })
            metrics.addView(line, LayoutParams(-1, -2).apply {
                if (metrics.childCount > 0) topMargin = dp(8)
            })
            metricTexts += name to amount
            return amount
        }
        weatherHighLow = metric("最高 / 最低")
        weatherHumidity = metric("湿度")
        weatherWind = metric("风速")
        card.addView(paired(temperature, metrics, { textWidth(value) }, {
            metricTexts.maxOf { (name, amount) -> textWidth(name) + dp(8) + textWidth(amount) }
        }), LayoutParams(-1, -2).apply { topMargin = dp(if (spec.compact) 8 else 12) })
        weatherUpdated = text("更新中", 11, secondary)
        card.addView(weatherUpdated, LayoutParams(-1, -2).apply { topMargin = dp(if (spec.compact) 8 else 12) })
        card.setOnClickListener { refreshWeather(true) }
        return card
    }

    private fun walletCard(spec: CardSpec): LinearLayout {
        val card = card(spec.compact)
        val heading = row()
        heading.addView(cardTitle("QQ钱包", MiuixIcons.BankCards), LayoutParams(0, -2, 1f))
        val eye = icon(if (hidden) MiuixIcons.Show else MiuixIcons.Hide, "切换余额可见性")
        eye.setOnClickListener {
            hidden = !hidden
            (eye as ImageView).setImageDrawable(MiuixIconRenderer.toDrawable(context,
                if (hidden) MiuixIcons.Show else MiuixIcons.Hide, dp(22)))
            updateWallet()
        }
        heading.addView(eye)
        card.addView(heading)
        val balance = column()
        val value = text("余额尚未读取", 20, foreground).apply { textDirection = View.TEXT_DIRECTION_LTR }
        walletValue = value
        balance.addView(value)
        walletUpdated = text("打开原生钱包以读取真实余额", 11, secondary)
        balance.addView(walletUpdated, LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        val open = text("打开钱包", NativeMiuixStyle.Body, colors.onSecondaryVariant.toArgb()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minimumHeight = dp(NativeMiuixStyle.TouchTarget)
            background = shape(colors.secondaryVariant.toArgb(), NativeMiuixStyle.ButtonCorner)
            setOnClickListener { bridge.clickLabel("钱包") }
        }
        card.addView(paired(balance, open, { textWidth(value) }, { textWidth(open) }, trailingShare = 0f),
            LayoutParams(-1, -2).apply { topMargin = dp(if (spec.compact) 8 else 12) })
        return card
    }

    private fun clockCard(spec: CardSpec): LinearLayout {
        val card = card(spec.compact, hasHeaderAction = false)
        card.addView(cardTitle(CardKind.CLOCK.title, MiuixIcons.Alarm))
        val value = text("", if (spec.compact) 27 else 32, foreground).apply { textDirection = View.TEXT_DIRECTION_LTR }
        val date = text("", NativeMiuixStyle.Body, secondary).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        clockValue = value; clockDate = date
        card.addView(paired(value, date, { textWidth(value) }, { textWidth(date) }),
            LayoutParams(-1, -2).apply { topMargin = dp(if (spec.compact) 8 else 12) })
        clockGreeting = text("", NativeMiuixStyle.Body, secondary)
        card.addView(clockGreeting, LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return card
    }

    private fun updateClock() {
        val value = clockValue ?: return
        val now = System.currentTimeMillis()
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val formatKey = "$locale:${TimeZone.getDefault().id}:${DateFormat.is24HourFormat(context)}"
        if (clockMinute == now / 60_000L && clockFormatKey == formatKey) return
        clockMinute = now / 60_000L; clockFormatKey = formatKey
        val date = Date(now)
        setText(value, DateFormat.getTimeFormat(context).format(date))
        clockDate?.let { setText(it, java.text.SimpleDateFormat(
            DateFormat.getBestDateTimePattern(locale, "MMMdEEE"), locale).format(date)) }
        clockGreeting?.let { setText(it, when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "早上好，开启轻松的一天。"
            in 11..13 -> "中午好，记得按时吃饭。"
            in 14..17 -> "下午好，愿今天顺利。"
            in 18..23 -> "晚上好，愿今晚安心入睡。"
            else -> "夜深了，记得好好休息。"
        }) }
    }

    private fun updateWeather() {
        val snapshot = weather
        weatherValue?.let { value ->
            val spec = layout.cards.first { it.kind == CardKind.WEATHER }
            value.textSize = if (snapshot == null) 18f else if (spec.compact) 34f else 42f
            setText(value, snapshot?.let { "${it.temperature.roundToInt()}°" }
                ?: if (weatherFailed) "读取失败，点击重试" else "读取天气中")
        }
        weatherDetails?.let { setText(it, snapshot?.let { "体感 ${it.apparent.roundToInt()}°" }.orEmpty()) }
        weatherCondition?.let { setText(it, snapshot?.let { CardWeather.description(it.code) }.orEmpty()) }
        weatherHighLow?.let { setText(it, snapshot?.let { "${it.high.roundToInt()}° / ${it.low.roundToInt()}°" } ?: "—") }
        weatherHumidity?.let { setText(it, snapshot?.let { "${it.humidity}%" } ?: "—") }
        weatherWind?.let { setText(it, snapshot?.let { "${it.wind.roundToInt()} km/h" } ?: "—") }
        weatherUpdated?.let { setText(it, if (weatherFailed) "更新失败" else snapshot?.let {
            "更新于 ${time(it.updatedAt)} · Open-Meteo"
        } ?: "更新中") }
    }

    private fun updateWallet() {
        walletValue?.let { value ->
            value.textSize = if (wallet == null) 19f else if (layout.cards.first { it.kind == CardKind.WALLET }.compact) 27f else 32f
            setText(value, if (hidden) "••••" else wallet?.let { "¥ ${it.amount}" } ?: "余额尚未读取")
        }
        walletUpdated?.let { setText(it, wallet?.let { "缓存 · 更新于 ${time(it.updatedAt)}" }
            ?: "打开原生钱包以读取真实余额") }
    }

    private fun refreshWeather(force: Boolean) {
        if (layout.cards.none { it.kind == CardKind.WEATHER && it.enabled } || weatherJob?.isActive == true) return
        weatherJob = ModuleScope.launchMain {
            weather = CardWeather.cached(config.weatherCity)
            updateWeather()
            if (!force && weather?.let { System.currentTimeMillis() - it.updatedAt < 600_000L } == true) return@launchMain
            try { weather = CardWeather.refresh(config.weatherCity); weatherFailed = false }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { weatherFailed = true }
            updateWeather()
        }
    }

    private fun edit() {
        NativeWidgetEditor(context, layout, dark) { draft ->
            runCatching { config.saveCardLayout(CardLayoutCodec.encode(draft)) }.onSuccess {
                layout = draft; render(); refreshWeather(false)
            }.onFailure { Log.w("SettingMeWidgets 布局保存失败: ${it.javaClass.simpleName}") }
        }.show()
    }

    private fun cardTitle(title: String, vector: ImageVector) = row().apply {
        val label = text(title, NativeMiuixStyle.Headline, this@NativeWidgets.foreground).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        addView(ImageView(context).apply {
            setImageDrawable(MiuixIconRenderer.toDrawable(context, vector, dp(NativeMiuixStyle.GlyphSize)))
            setColorFilter(secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(NativeMiuixStyle.GlyphSize), dp(NativeMiuixStyle.GlyphSize)).apply {
            marginEnd = dp(NativeMiuixStyle.IconTextGap)
        })
        addView(label, LayoutParams(0, -2, 1f))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun card(compact: Boolean, hasHeaderAction: Boolean = true) = LinearLayout(context).apply {
        orientation = VERTICAL
        val visibleInset = if (compact) 16 else 20
        // A centered 22dp glyph already adds 11dp inside the 44dp header target.
        // Count that space once so visible content has equal top/bottom insets.
        val headerInset = if (hasHeaderAction)
            (NativeMiuixStyle.TouchTarget - NativeMiuixStyle.GlyphSize) / 2 else 0
        setPadding(dp(WidgetInset), dp((visibleInset - headerInset).coerceAtLeast(0)),
            dp(WidgetInset), dp(visibleInset))
        background = shape((if (dark) colors.surfaceContainer else colors.surface).toArgb(), NativeMiuixStyle.CardCorner)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(NativeMiuixStyle.CardGap) }
    }
    /** Keep the pair readable at its actual width, including large values and font scaling. */
    private fun paired(leading: View, trailing: View, leadingMinimum: () -> Int,
                       trailingMinimum: () -> Int, trailingShare: Float = .5f) = object : LinearLayout(context) {
        init {
            gravity = Gravity.CENTER_VERTICAL
            addView(leading, LayoutParams(0, -2))
            addView(trailing, LayoutParams(0, -2))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec)
            val gap = dp(12)
            val start = leadingMinimum().coerceAtLeast(dp(64))
            val end = trailingMinimum().coerceAtLeast(dp(44))
            val stacked = width < start + end + gap
            orientation = if (stacked) VERTICAL else HORIZONTAL
            val startParams = leading.layoutParams as LayoutParams
            val endParams = trailing.layoutParams as LayoutParams
            val endWidth = if (stacked) 0 else ((width - gap) * trailingShare).roundToInt()
                .coerceIn(end, width - gap - start)
            startParams.width = if (stacked) LayoutParams.MATCH_PARENT else width - gap - endWidth
            endParams.width = if (stacked) LayoutParams.MATCH_PARENT else endWidth
            endParams.marginStart = if (stacked) 0 else gap
            endParams.topMargin = if (stacked) dp(8) else 0
            endParams.resolveLayoutDirection(layoutDirection)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun column() = LinearLayout(context).apply { orientation = VERTICAL }
    private fun textWidth(view: TextView) = ceil(view.paint.measureText(view.text.toString())).toInt() +
        view.paddingStart + view.paddingEnd
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Int, color: Int) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.START or Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }
    private fun icon(vector: ImageVector, description: String, alignVisibleEnd: Boolean = false,
                     action: (() -> Unit)? = null): View = ImageView(context).apply {
        contentDescription = description
        val glyphSize = dp(NativeMiuixStyle.GlyphSize)
        val glyph = MiuixIconRenderer.toDrawable(context, vector, glyphSize)
        setImageDrawable(glyph)
        if (alignVisibleEnd && glyph is BitmapDrawable) {
            val bitmap = glyph.bitmap
            val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            var transparentInset = 0
            while (transparentInset < bitmap.width) {
                val x = if (rtl) transparentInset else bitmap.width - 1 - transparentInset
                if ((0 until bitmap.height).any { y -> Color.alpha(bitmap.getPixel(x, y)) >= 128 }) break
                transparentInset++
            }
            if (transparentInset < bitmap.width) {
                scaleType = ImageView.ScaleType.MATRIX
                imageMatrix = Matrix().apply {
                    val scale = glyphSize.toFloat() / glyph.intrinsicWidth.coerceAtLeast(1)
                    setScale(scale, scale)
                    val inset = transparentInset * glyphSize.toFloat() / bitmap.width
                    postTranslate(if (rtl) -inset else inset, 0f)
                }
            }
        }
        setColorFilter(accent)
        val inset = dp((NativeMiuixStyle.TouchTarget - NativeMiuixStyle.GlyphSize) / 2)
        setPaddingRelative(dp(NativeMiuixStyle.TouchTarget - NativeMiuixStyle.GlyphSize), inset, 0, inset)
        layoutParams = LayoutParams(dp(44), dp(44))
        action?.let { setOnClickListener { it() } }
    }
    private fun shape(color: Int, radius: Int) = NativeMiuixSurface(color, dp(radius).toFloat())
    private fun setText(view: TextView, value: String) { if (view.text.toString() != value) view.text = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun time(value: Long) = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(value))

    private companion object { const val WidgetInset = 28 }
}
