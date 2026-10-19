package com.maizon.maizosnes

import org.junit.Assert.*
import org.junit.Test

class OnlineRegistryTest {
    @Test fun parsesMultipleApprovedHttpsRelays() {
        val json = """{"version":1,"servers":[
            {"name":"Brasil","region":"BR","url":"https://br.example.org"},
            {"name":"Europa","region":"EU","url":"https://eu.example.org"},
            {"name":"Off","region":"US","url":"https://no.example.org","enabled":false}
        ]}"""
        val servers = OnlineRegistry.parseRegistry(json)
        assertEquals(2, servers.size)
        assertEquals("BR", servers[0].region)
        assertEquals("https://eu.example.org", servers[1].endpoint)
    }
    @Test fun excludesDuplicateAndEmptyEntries() {
        val json = """{"version":1,"servers":[
            {"name":"A","url":"https://a.example"},
            {"name":"B","url":"https://a.example"},
            {"name":"","url":"https://c.example"}
        ]}"""
        assertEquals(1, OnlineRegistry.parseRegistry(json).size)
        assertTrue(OnlineRegistry.parseRegistry("""{"version":1,"servers":[]}""").isEmpty())
    }
    @Test fun rejectsPlaintextOrCredentialsInPublicRegistry() {
        for (bad in listOf("http://public.example", "https://u:p@host.example",
            "https://public.example/another/path")) {
            assertThrows(IllegalArgumentException::class.java) {
                OnlineRegistry.parseRegistry(
                    """{"version":1,"servers":[{"name":"x","url":"$bad"}]}""")
            }
        }
    }
    @Test fun wssProbeDoesNotDependOnLegacyTcpProxy() {
        val wssOnly = RelayListing("unreachable.proxy.example", 8788,
            emptyList(), tls = false, wss = true)
        assertEquals(RelayProbe.WSS, OnlineRegistry.preferredProbe(wssOnly))
        assertEquals(RelayProbe.WSS, OnlineRegistry.preferredProbe(
            wssOnly.copy(tls = true)))
    }
    @Test fun legacyTransportsStillUseTcpAndVerifyTlsWhenAdvertised() {
        val tcp = RelayListing("legacy.example", 8788, emptyList())
        assertEquals(RelayProbe.TCP, OnlineRegistry.preferredProbe(tcp))
        assertEquals(RelayProbe.TLS_TCP, OnlineRegistry.preferredProbe(
            tcp.copy(tls = true)))
    }
}
