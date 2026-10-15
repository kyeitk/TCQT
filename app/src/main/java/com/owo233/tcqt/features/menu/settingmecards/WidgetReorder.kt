package com.owo233.tcqt.features.menu.settingmecards

internal val editableWidgetKinds = setOf(CardKind.WEATHER, CardKind.WALLET, CardKind.CLOCK)

/** Move only the editable slots; unimplemented cards keep their stored positions. */
internal fun CardLayout.reorderWidget(source: CardKind, target: CardKind, after: Boolean): CardLayout {
    val supported = editableWidgetKinds
    if (source !in supported || target !in supported || source == target) return this
    val slots = cards.indices.filter { cards[it].kind in supported }
    val ordered = slots.map { cards[it] }.toMutableList()
    val moving = ordered.firstOrNull { it.kind == source } ?: return this
    ordered.remove(moving)
    val targetIndex = ordered.indexOfFirst { it.kind == target }
    if (targetIndex < 0) return this
    ordered.add(targetIndex + if (after) 1 else 0, moving)
    val next = cards.toMutableList()
    slots.forEachIndexed { index, slot -> next[slot] = ordered[index] }
    return copy(cards = next)
}
