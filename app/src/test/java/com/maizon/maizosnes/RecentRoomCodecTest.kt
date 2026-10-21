package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class RecentRoomCodecTest {
    @Test fun lastVisitedRoomIsLocalAndBounded() {
        val a = RecentRoom("abcdef123456", "Mario", "Maizon", "https://games.example", 1L)
        val b = RecentRoom("123456abcdef", "Donkey Kong", "Friend", "https://games.example", 2L)
        val result = RecentRoomCodec.withVisited(listOf(a, b), a.copy(visitedAt = 3L))
        assertEquals(2, result.size)
        assertEquals(3L, result.first().visitedAt)
        val decoded = RecentRoomCodec.decode(RecentRoomCodec.encode(result))
        assertEquals(2, decoded.size)
        assertEquals("Maizon", decoded[0].ownerName)
    }
    @Test fun malformedEntriesAreIgnored() {
        assertEquals(0, RecentRoomCodec.decode("[{\"id\":\"oops\"}]").size)
        assertEquals(0, RecentRoomCodec.decode("bad").size)
    }
}
