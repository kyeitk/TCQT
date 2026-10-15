package com.owo233.tcqt.features.menu.settingmecards

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class CardKind(val title: String) {
    WEATHER("天气"), WALLET("钱包"), MENU("常用功能"), CLOCK("时间问候"),
    MEMBERSHIP("会员等级"), APPEARANCE("个性装扮"),
}

@Serializable
internal data class CardSpec(
    val kind: CardKind,
    val enabled: Boolean = true,
    val compact: Boolean = false,
)

@Serializable
internal data class CardLayout(val version: Int = 1, val cards: List<CardSpec>) {
    fun move(kind: CardKind, to: Int): CardLayout {
        val from = cards.indexOfFirst { it.kind == kind }
        if (from < 0 || to !in cards.indices || from == to) return this
        return copy(cards = cards.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun update(kind: CardKind, block: (CardSpec) -> CardSpec): CardLayout =
        copy(cards = cards.map { if (it.kind == kind) block(it) else it })

    companion object {
        fun defaults() = CardLayout(cards = CardKind.entries.map {
            CardSpec(it, enabled = it in setOf(CardKind.WEATHER, CardKind.WALLET, CardKind.MENU))
        })
    }
}

internal object CardLayoutCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(layout: CardLayout): String {
        require(layout.version == 1)
        require(layout.cards.map { it.kind }.toSet() == CardKind.entries.toSet())
        require(layout.cards.size == CardKind.entries.size)
        return json.encodeToString(CardLayout.serializer(), layout)
    }

    // The caller leaves invalid stored data intact until the user explicitly saves an edit.
    fun decode(raw: String): CardLayout = runCatching {
        json.decodeFromString(CardLayout.serializer(), raw).also { encode(it) }
    }.getOrElse { CardLayout.defaults() }
}
