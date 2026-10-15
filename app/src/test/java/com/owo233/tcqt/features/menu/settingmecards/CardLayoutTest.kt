package com.owo233.tcqt.features.menu.settingmecards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardLayoutTest {
    @Test
    fun `reorder and customization survive saving`() {
        val layout = CardLayout.defaults().move(CardKind.WALLET, 0)
            .update(CardKind.WEATHER) { it.copy(compact = true) }
            .update(CardKind.APPEARANCE) { it.copy(enabled = true) }
        assertEquals(layout, CardLayoutCodec.decode(CardLayoutCodec.encode(layout)))
        assertEquals(CardKind.WALLET, layout.cards.first().kind)
        assertTrue(layout.cards.single { it.kind == CardKind.WEATHER }.compact)
    }

    @Test
    fun `discarding a draft leaves the saved layout unchanged`() {
        val saved = CardLayout.defaults()
        val draft = saved.move(CardKind.WALLET, 0)
            .update(CardKind.MENU) { it.copy(enabled = false) }
        assertEquals(CardKind.WEATHER, saved.cards.first().kind)
        assertTrue(saved.cards.single { it.kind == CardKind.MENU }.enabled)
        assertFalse(draft.cards.single { it.kind == CardKind.MENU }.enabled)
    }

    @Test
    fun `duplicate missing and future layouts fall back to usable defaults`() {
        val raw = CardLayoutCodec.encode(CardLayout.defaults())
        assertEquals(CardLayout.defaults(), CardLayoutCodec.decode(raw.replace("WEATHER", "WALLET")))
        assertEquals(CardLayout.defaults(), CardLayoutCodec.decode("{\"version\":2,\"cards\":[]}"))
        assertEquals(CardLayout.defaults(), CardLayoutCodec.decode("{broken"))
        assertEquals(CardLayout.defaults(), CardLayoutCodec.decode(""))
    }
}
