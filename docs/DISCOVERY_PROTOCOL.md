# LAN discovery protocol: implementation inventory and validation

This file is a **verification checklist**, not a replacement wire specification. The authoritative implementation is `app/src/main/java/com/maizon/maizosnes/LanDiscovery.kt`; the control-channel implementation is `NetplaySession.kt`.

## Separation of channels

| Channel | Purpose | Current documented default |
| --- | --- | --- |
| UDP discovery broadcast | Advertise/discover LAN rooms | 55436 |
| TCP control | ROM rights, ROM/state transfer, negotiation and Lockstep fallback | 55435 |
| GekkoNet UDP | Direct rollback game packets after successful TCP negotiation | Dynamic; must not assume discovery port |
| Railway HTTPS/WSS | Online room discovery, signaling and TCP fallback | Configured relay endpoint |

## Compatibility and safety

- Direct LAN/IP negotiation uses MZNP v7 for rollback-aware control. The online WSS/Lockstep layout may use a different version; do not silently change wire formats.
- Advertisements are untrusted. Verify complete ROM SHA-256, protocol version, core settings and authorization over the control channel.
- Discovery must never transfer ROM bytes or disclose ROM contents.
- Client and host must agree on rollback vs Lockstep; a unilateral fallback can deadlock.
- When no compatible room is found, retain manual IP:port entry.
- AP isolation is a possible explanation for missing discovery/UDP, not a diagnosis from a single timeout.

## Required device validation

1. Two devices on the same Wi-Fi discover a room, join and play for 10 minutes.
2. Disable broadcast: manual TCP IP:port still connects.
3. Block UDP: both peers fall back to TCP on the same session.
4. Host/client with different rollback preferences negotiate Lockstep.
5. Transfer a redistributable homebrew ROM only after host authorization and client consent; verify destination SHA-256.
6. Confirm the debug overlay reports the *actual* transport.

## Verified datagram format (LanDiscovery.kt)

- UDP port: `55436`. Scanner sends the ASCII probe `MZSCAN2` as a broadcast datagram (global and interface-specific broadcast destinations), repeating about every 650 ms during the scan window.
- Advertiser replies only to probes from site-local addresses. Reply is UTF-8: `MZOFFER2|<tcpPort>|<gameName>|<romHashPrefix>|<cheats>`.
- `tcpPort` must be between 1024 and 65535; `gameName` is sanitized to 76 characters; `romHashPrefix` is either empty or exactly 16 lowercase hexadecimal characters; `cheats` is `1` or `0`.
- Datagram receive buffer: 512 bytes. Default scan window: 1750 ms, clamped to 250–5000 ms; socket receive timeout: 220 ms.
- The scanner accepts replies from site-local addresses only, deduplicates by `host:port` and sorts by game then host.
- This is **discovery v2**, independent of the **MZNP v7 TCP negotiation protocol**. Neither probe nor offer transfers ROM contents.
- Parsing behavior has been read from the implementation; physical discovery and packet-loss resilience remain **unverified on two devices**.

