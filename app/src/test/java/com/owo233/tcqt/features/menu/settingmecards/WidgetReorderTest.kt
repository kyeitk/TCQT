package com.owo233.tcqt.features.menu.settingmecards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WidgetReorderTest {
    @Test
    fun `insertion before and after a target follows the indicated slot`() {
        val original = CardLayout.defaults()
        val moved = original.reorderWidget(CardKind.WEATHER, CardKind.WALLET, after = true)
        assertEquals(CardKind.WALLET, moved.cards.first().kind)
        assertEquals(original, moved.reorderWidget(CardKind.WEATHER, CardKind.WALLET, after = false))
        assertEquals(original, original.reorderWidget(CardKind.WEATHER, CardKind.WALLET, after = false))
    }

    @Test
    fun `reordering preserves optional slots and disabled widget settings`() {
        val original = CardLayout.defaults().move(CardKind.MENU, 1)
            .update(CardKind.WALLET) { it.copy(enabled = false, compact = true) }
        val next = original.reorderWidget(CardKind.WALLET, CardKind.WEATHER, after = false)
        assertEquals(original.cards[1], next.cards[1])
        assertEquals(original.cards.first { it.kind == CardKind.WALLET }, next.cards.first())
        assertFalse(next.cards.first().enabled)
        assertEquals(next, CardLayoutCodec.decode(CardLayoutCodec.encode(next)))
    }

    @Test
    fun `cancelled drag and unsupported sources do not change the saved layout`() {
        val saved = CardLayout.defaults()
        val draft = saved.reorderWidget(CardKind.WALLET, CardKind.WEATHER, after = false)
        assertEquals(CardKind.WEATHER, saved.cards.first().kind)
        assertEquals(CardKind.WALLET, draft.cards.first().kind)
        assertEquals(saved, saved.reorderWidget(CardKind.MENU, CardKind.WEATHER, after = false))
        assertEquals(saved, saved.reorderWidget(CardKind.WEATHER, CardKind.WEATHER, after = true))
    }

    @Test
    fun `clock participates in ordering and retains its saved display settings`() {
        val saved = CardLayout.defaults().update(CardKind.CLOCK) { it.copy(enabled = true, compact = true) }
        val next = saved.reorderWidget(CardKind.CLOCK, CardKind.WALLET, after = false)
        assertEquals(listOf(CardKind.WEATHER, CardKind.CLOCK, CardKind.WALLET),
            next.cards.filter { it.kind in editableWidgetKinds }.map { it.kind })
        assertEquals(saved.cards.first { it.kind == CardKind.CLOCK }, next.cards[1])
        assertEquals(saved.cards.filter { it.kind !in editableWidgetKinds },
            next.cards.filter { it.kind !in editableWidgetKinds })
        assertEquals(next, CardLayoutCodec.decode(CardLayoutCodec.encode(next)))
    }

    @Test
    fun `a widget can move past both other editable cards`() {
        val saved = CardLayout.defaults()
        val next = saved.reorderWidget(CardKind.WEATHER, CardKind.CLOCK, after = true)
        assertEquals(listOf(CardKind.WALLET, CardKind.CLOCK, CardKind.WEATHER),
            next.cards.filter { it.kind in editableWidgetKinds }.map { it.kind })
        assertEquals(saved, next.reorderWidget(CardKind.WEATHER, CardKind.WALLET, after = false))
    }
}
