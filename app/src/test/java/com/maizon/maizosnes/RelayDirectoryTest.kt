package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RelayDirectoryTest {
    @Test fun bareIpDefaultsToPort8787() {
        assertEquals("http://192.0.2.12:8787/api/rooms",
            RelayDirectory.endpoint("192.0.2.12").toString())
    }
    @Test fun customPortAndHttpsRemainUnchanged() {
        assertEquals("http://myserver.local:9000/api/rooms",
            RelayDirectory.endpoint("myserver.local:9000").toString())
        assertEquals("https://room.example/api/rooms",
            RelayDirectory.endpoint("https://room.example").toString())
    }
    @Test fun rejectInjectedCredentialsAndPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            RelayDirectory.endpoint("http://admin:pass@host:8787")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RelayDirectory.endpoint("http://host:8787/untrusted/path")
        }
    }
}
