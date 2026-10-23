package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class RemoteLanWireTest {
    @Test fun helloRoundTripPreservesNicknameWithoutRom() {
        val packet = RemoteLanWire.encode(RemoteLanWire.Message(
            RemoteLanWire.HELLO, nonce = 42L, text = "Jogador Maizon"))
        val result = RemoteLanWire.decode(packet)
        assertNotNull(result)
        assertEquals(RemoteLanWire.HELLO, result!!.type)
        assertEquals(42L, result.nonce)
        assertEquals("Jogador Maizon", result.text)
        assertTrue(packet.size < 256)
    }

    @Test fun inputEncodesCompleteSixteenBitState() {
        val packet = RemoteLanWire.encode(RemoteLanWire.Message(
            RemoteLanWire.INPUT, token = 112L, sequence = 7, buttons = 0x8401))
        val result = RemoteLanWire.decode(packet)!!
        assertEquals(112L, result.token)
        assertEquals(7, result.sequence)
        assertEquals(0x8401, result.buttons)
    }

    @Test fun approvalCarriesSlotAndDecorativeTitleOnly() {
        val packet = RemoteLanWire.encode(RemoteLanWire.Message(
            RemoteLanWire.ACCEPT, nonce = 123, token = 456,
            slot = 2, text = "Super Mario World"))
        val result = RemoteLanWire.decode(packet)!!
        assertEquals(123L, result.nonce)
        assertEquals(456L, result.token)
        assertEquals(2, result.slot)
        assertEquals("Super Mario World", result.text)
    }

    @Test fun independentP3P4ReservationsUseExistingWireVersion() {
        for (slot in 2..4) {
            val packet = RemoteLanWire.encode(RemoteLanWire.Message(
                RemoteLanWire.ACCEPT, nonce = slot.toLong(), token = slot * 100L,
                slot = slot, text = "Super Bomberman"))
            val result = RemoteLanWire.decode(packet)!!
            assertEquals(slot, result.slot)
            assertEquals(slot * 100L, result.token)
            assertEquals("Super Bomberman", result.text)
        }
    }

    @Test fun rejectsMalformedOrTrailingPackets() {
        val good = RemoteLanWire.encode(RemoteLanWire.Message(RemoteLanWire.WAIT, nonce = 5))
        assertNull(RemoteLanWire.decode(byteArrayOf(0x01, 0x02, 0x03)))
        assertNull(RemoteLanWire.decode(good.copyOfRange(0, good.size - 2)))
        assertNull(RemoteLanWire.decode(good + byteArrayOf(0)))
        val badMagic = good.clone()
        badMagic[0] = 0
        assertNull(RemoteLanWire.decode(badMagic))
        val badVersion = good.clone()
        badVersion[4] = 2
        assertNull(RemoteLanWire.decode(badVersion))
    }

    @Test fun signedButtonMasksAreClampedToSixteenBits() {
        val packet = RemoteLanWire.encode(RemoteLanWire.Message(
            RemoteLanWire.INPUT, token = 9, sequence = 10, buttons = -1))
        assertEquals(0xffff, RemoteLanWire.decode(packet)!!.buttons)
    }
}
