package com.owo233.tcqt.features.menu.settingmecards

/** QQ renders the fractional part in a second, smaller TextView. */
internal object NativeBalanceText {
    private val amount = Regex("(?:[¥￥]\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)")
    private val fraction = Regex("\\.[0-9]{1,2}")

    fun parse(texts: List<String>): String? {
        if (texts.none { it == "余额" || it == "账户余额" }) return null
        val whole = texts.mapNotNull { amount.matchEntire(it)?.groupValues?.get(1) }
        val fractions = texts.filter(fraction::matches)
        if (whole.size != 1 || fractions.size > 1) return null
        val value = whole.single().replace(",", "")
        if (fractions.isEmpty()) return value
        if ('.' in value) return null
        return value + fractions.single()
    }
}
