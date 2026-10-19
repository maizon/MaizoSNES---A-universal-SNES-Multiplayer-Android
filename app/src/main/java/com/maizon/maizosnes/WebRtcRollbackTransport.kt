package com.maizon.maizosnes

import android.content.Context
import livekit.org.webrtc.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebRTC data-only channel with the existing Railway WSS relay as SDP/ICE
 * signaling. It NEVER transmits ROMs: the original MZNP WSS stream handles
 * rights consent, byte transport and SHA-256 before rollback is attempted.
 *
 * Signaling uses an independent, deterministic hidden WSS room. This leaves
 * the original MZNP wire format, TCP fallback and manual IP:port untouched.
 */
internal class WebRtcRollbackTransport private constructor(
    private val pc: PeerConnection,
    private val incoming: ArrayBlockingQueue<ByteArray>,
    private val connected: CountDownLatch
) : AutoCloseable {
    private val disposed = AtomicBoolean(false)
    @Volatile private var channel: DataChannel? = null
    @Volatile var iceState: String = "new"
        private set
    @Volatile var turnUsed: String = "N/D"
        private set
    @Volatile var natType: String = "N/D"
        private set

    fun refreshIceStats() {
        if (disposed.get()) return
        pc.getStats { report ->
            if (disposed.get()) return@getStats
            val records = report.statsMap
            val selected = records.values.firstOrNull { stat ->
                stat.type == "transport" &&
                    stat.members["selectedCandidatePairId"] is String
            }?.members?.get("selectedCandidatePairId") as? String
            val pair = selected?.let(records::get) ?: records.values.firstOrNull { stat ->
                stat.type == "candidate-pair" &&
                    stat.members["nominated"] == true &&
                    stat.members["state"] == "succeeded"
            }
            val local = pair?.members?.get("localCandidateId") as? String
            val remote = pair?.members?.get("remoteCandidateId") as? String
            val localType = local?.let(records::get)?.members?.get("candidateType") as? String
            val remoteType = remote?.let(records::get)?.members?.get("candidateType") as? String
            if (localType != null || remoteType != null) {
                turnUsed = if (localType == "relay" || remoteType == "relay")
                    "Sim (relay TURN)" else "Não (peer direto)"
                natType = "ICE selecionado; tipo NAT não detectável"
            }
        }
    }

    /** These methods form the JNI GekkoNetAdapter contract. */
    fun send(packet: ByteArray): Boolean {
        val dc = channel ?: return false
        if (disposed.get() || dc.state() != DataChannel.State.OPEN ||
            dc.bufferedAmount() > 512 * 1024L || packet.size > 16384) return false
        return dc.send(DataChannel.Buffer(ByteBuffer.wrap(packet), true))
    }
    fun takePacket(): ByteArray? = incoming.poll()

    private fun bind(dc: DataChannel) {
        channel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                if (dc.state() == DataChannel.State.OPEN) connected.countDown()
            }
            override fun onMessage(message: DataChannel.Buffer) {
                if (!message.binary || disposed.get()) return
                val size = message.data.remaining()
                if (size !in 1..16384) return
                val bytes = ByteArray(size)
                message.data.get(bytes)
                // Bounded packet queue: a stalled peer cannot exhaust memory.
                incoming.offer(bytes)
            }
        })
        if (dc.state() == DataChannel.State.OPEN) connected.countDown()
    }

    override fun close() {
        if (!disposed.compareAndSet(false, true)) return
        runCatching { channel?.unregisterObserver() }
        runCatching { channel?.close() }
        runCatching { channel?.dispose() }
        runCatching { pc.close() }
        runCatching { pc.dispose() }
        incoming.clear()
    }

    companion object {
        private val initLock = Any()
        @Volatile private var initialized = false
        @Volatile private var persistentFactory: PeerConnectionFactory? = null

        private fun buildFactory(context: Context): PeerConnectionFactory {
            synchronized(initLock) {
                if (!initialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions
                            .builder(context.applicationContext)
                            .createInitializationOptions())
                    initialized = true
                }
            }
            synchronized(initLock) {
                persistentFactory?.let { return it }
                return PeerConnectionFactory.builder().createPeerConnectionFactory()
                    .also { persistentFactory = it }
            }
        }

        private fun signalRoom(id: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(("gekko-rtc-v1:" + id).toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }

        private fun openSignaling(endpoint: String, roomId: String, host: Boolean):
            WssRelaySocket {
            val signalId = signalRoom(roomId)
            var last: Exception? = null
            repeat(if (host) 1 else 6) { attempt ->
                var sock: WssRelaySocket? = null
                try {
                    sock = WssRelaySocket.connect(endpoint)
                    sock.soTimeout = if (host) 25000 else 4000
                    val title = "R2Vra29TaWduYWxpbmc" // GekkoSignaling, hidden from room listing
                    val greeting = if (host)
                        "HOST $signalId $title 0000000000000000 0\n"
                    else "JOIN $signalId\n"
                    sock.getOutputStream().write(greeting.toByteArray(Charsets.US_ASCII))
                    sock.getOutputStream().flush()
                    val answer = StringBuilder()
                    while (answer.length < 96) {
                        val b = sock.getInputStream().read()
                        if (b < 0 || b == 10) break
                        answer.append(b.toChar())
                    }
                    if (answer.toString() == "READY") {
                        sock.soTimeout = 50000
                        return sock
                    }
                    throw java.io.IOException("Sinalização WSS: " + answer)
                } catch (err: Exception) {
                    sock?.close()
                    last = err
                    if (!host && attempt < 5) Thread.sleep(650)
                }
            }
            throw last ?: java.io.IOException("Falha na sinalização WSS")
        }

        private fun sendSdp(out: DataOutputStream, sdp: SessionDescription) {
            val bytes = sdp.description.toByteArray(Charsets.UTF_8)
            require(bytes.size in 16..64000) { "SDP excessivo/inválido" }
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
        private fun receiveSdp(input: DataInputStream, type: SessionDescription.Type):
            SessionDescription {
            val len = input.readInt()
            require(len in 16..64000) { "SDP remoto inválido" }
            val bytes = ByteArray(len)
            input.readFully(bytes)
            return SessionDescription(type, String(bytes, Charsets.UTF_8))
        }
        private fun awaitSdpCreate(peer: PeerConnection, host: Boolean):
            SessionDescription {
            var value: SessionDescription? = null
            var error: String? = null
            val done = CountDownLatch(1)
            val observer = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    value = sdp; done.countDown()
                }
                override fun onCreateFailure(msg: String) {
                    error = msg; done.countDown()
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(msg: String) {}
            }
            if (host) peer.createOffer(observer, MediaConstraints())
            else peer.createAnswer(observer, MediaConstraints())
            require(done.await(9, TimeUnit.SECONDS) && value != null) {
                "Não criou SDP: " + error
            }
            return value!!
        }
        private fun setSdp(peer: PeerConnection, sdp: SessionDescription,
                           local: Boolean) {
            var success = false
            var error: String? = null
            val done = CountDownLatch(1)
            val observer = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {}
                override fun onCreateFailure(msg: String) {}
                override fun onSetSuccess() { success = true; done.countDown() }
                override fun onSetFailure(msg: String) {
                    error = msg; done.countDown()
                }
            }
            if (local) peer.setLocalDescription(observer, sdp)
            else peer.setRemoteDescription(observer, sdp)
            require(done.await(9, TimeUnit.SECONDS) && success) {
                "Não aplicou SDP: " + error
            }
        }

        /**
         * Returns null on NAT/ICE/signaling failure without touching the main
         * MZNP connection; both peers continue lockstep automatically.
         */
        /** Both devices should use identical ICE configuration (Railway defaults).
         * Attempts prefer direct ICE first, then relay-only if TURN is present.
         * Missing/invalid provider credentials never block the TCP fallback.
         */
        fun attempt(context: Context, endpoint: String, roomId: String,
                    hosting: Boolean, onState: (String) -> Unit,
                    customIce: String = ""): WebRtcRollbackTransport? {
            val ice = IceServerConfig.load(endpoint, customIce)
            val haveTurn = IceServerConfig.hasTurn(ice)
            onState("ICE: verificando STUN" +
                if (haveTurn) " + TURN autenticado." else " (TURN indisponível).")
            val direct = attemptOnce(context, endpoint, roomId, hosting, ice,
                relayOnly = false, onState = onState)
            if (direct.transport != null) return direct.transport
            if (!direct.canRetryRelay || !haveTurn) {
                onState("ICE checking excedeu prazo; Lockstep TCP via Railway.")
                return null
            }
            onState("ICE: tentando TURN relay exclusivamente antes de Lockstep…")
            return attemptOnce(context, endpoint, roomId, hosting, ice,
                relayOnly = true, onState = onState).transport
        }

        private data class AttemptResult(
            val transport: WebRtcRollbackTransport? = null,
            val canRetryRelay: Boolean = false
        )

        private fun attemptOnce(context: Context, endpoint: String, roomId: String,
                    hosting: Boolean, ice: List<PeerConnection.IceServer>,
                    relayOnly: Boolean, onState: (String) -> Unit): AttemptResult {
            // Deliberately different rendezvous for retry: cannot mix stale
            // SDP/ICE packets of a previous failed attempt.
            val channelId = roomId + if (relayOnly) ":turn" else ":direct"
            val signal = try { openSignaling(endpoint, channelId, hosting) }
                catch (err: Exception) {
                    onState("Signaling WSS indisponível: " + err.javaClass.simpleName)
                    return AttemptResult()
                }
            var transport: WebRtcRollbackTransport? = null
            var success = false
            var canRetryRelay = false
            try {
                signal.use { ws ->
                    val input = DataInputStream(ws.getInputStream())
                    val output = DataOutputStream(ws.getOutputStream())
                    val ownTurn = IceServerConfig.hasTurn(ice)
                    output.writeBoolean(ownTurn)
                    output.flush()
                    val peerTurn = input.readBoolean()
                    canRetryRelay = ownTurn && peerTurn
                    if (relayOnly && !canRetryRelay) return AttemptResult()

                    val factory = buildFactory(context)
                    val outgoing = LinkedBlockingQueue<IceCandidate>(256)
                    val open = CountDownLatch(1)
                    val partnerComplete = CountDownLatch(1)
                    val partnerAccepted = AtomicBoolean(false)
                    val incoming = ArrayBlockingQueue<ByteArray>(384)
                    val config = PeerConnection.RTCConfiguration(ice)
                    config.iceTransportsType = if (relayOnly)
                        PeerConnection.IceTransportsType.RELAY else
                        PeerConnection.IceTransportsType.ALL
                    var connection: WebRtcRollbackTransport? = null
                    val peer = factory.createPeerConnection(config,
                        object : PeerConnection.Observer {
                            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                                connection?.iceState = s.name.lowercase()
                                onState("ICE: " + s.name.lowercase() +
                                    if (relayOnly) " (TURN relay)" else " (STUN/TURN)")
                            }
                            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {
                                onState("ICE gathering: " + s.name.lowercase())
                            }
                            override fun onIceCandidate(candidate: IceCandidate) {
                                if (candidate.sdp.length < 4096) outgoing.offer(candidate)
                            }
                            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                            override fun onAddStream(stream: MediaStream) {}
                            override fun onRemoveStream(stream: MediaStream) {}
                            override fun onDataChannel(dc: DataChannel) {
                                connection?.bind(dc)
                            }
                            override fun onRenegotiationNeeded() {}
                        }) ?: error("WebRTC PeerConnection não disponível")

                    transport = WebRtcRollbackTransport(peer, incoming, open)
                    connection = transport
                    if (hosting) {
                        val init = DataChannel.Init().apply {
                            ordered = false
                            maxRetransmits = 0
                        }
                        val dc = peer.createDataChannel("gekko-udp", init)
                            ?: error("Falha ao criar DataChannel")
                        connection.bind(dc)
                    }
                    // Send SDP immediately; candidates are exchanged with
                    // trickle ICE after the SDP offer/answer is installed.
                    if (hosting) {
                        val offer = awaitSdpCreate(peer, true)
                        setSdp(peer, offer, true)
                        sendSdp(output, offer)
                        setSdp(peer,
                            receiveSdp(input, SessionDescription.Type.ANSWER), false)
                    } else {
                        setSdp(peer,
                            receiveSdp(input, SessionDescription.Type.OFFER), false)
                        val answer = awaitSdpCreate(peer, false)
                        setSdp(peer, answer, true)
                        sendSdp(output, answer)
                    }

                    val alive = AtomicBoolean(true)
                    val writer = Thread({
                        try {
                            while (alive.get()) {
                                val candidate = outgoing.poll(100, TimeUnit.MILLISECONDS)
                                    ?: continue
                                synchronized(output) {
                                    output.writeByte(1)
                                    output.writeUTF(candidate.sdpMid ?: "")
                                    output.writeInt(candidate.sdpMLineIndex)
                                    output.writeUTF(candidate.sdp)
                                    output.flush()
                                }
                            }
                        } catch (_: Exception) { alive.set(false) }
                    }, "Maizo-RTC-ICE-Sender").apply { isDaemon = true; start() }

                    val reader = Thread({
                        try {
                            while (alive.get()) {
                                when (input.readUnsignedByte()) {
                                    1 -> {
                                        val mid = input.readUTF()
                                        val index = input.readInt()
                                        val sdp = input.readUTF()
                                        require(mid.length < 100 && index in 0..16 &&
                                            sdp.length < 4096) { "ICE candidate inválido" }
                                        peer.addIceCandidate(IceCandidate(mid, index, sdp))
                                    }
                                    3 -> {
                                        partnerAccepted.set(input.readBoolean())
                                        partnerComplete.countDown()
                                        break
                                    }
                                    else -> error("ICE message desconhecida")
                                }
                            }
                        } catch (_: Exception) {
                            partnerComplete.countDown()
                        }
                    }, "Maizo-RTC-ICE-Receiver").apply { isDaemon = true; start() }

                    val ready = open.await(if (relayOnly) 14 else 17, TimeUnit.SECONDS) &&
                        connection.channel?.state() == DataChannel.State.OPEN
                    synchronized(output) {
                        output.writeByte(3)
                        output.writeBoolean(ready)
                        output.flush()
                    }
                    val partnerReady = partnerComplete.await(7, TimeUnit.SECONDS) &&
                        partnerAccepted.get()
                    alive.set(false)
                    if (!ready || !partnerReady) {
                        onState(if (relayOnly)
                            "ICE relay TURN falhou; retornando Lockstep TCP."
                            else "ICE P2P não completou; avaliando TURN.")
                        return AttemptResult(canRetryRelay = canRetryRelay)
                    }
                    success = true
                    onState("ICE connected: WebRTC DataChannel estabelecido" +
                        if (relayOnly) " pelo TURN" else " (par ICE selecionado)")
                    return AttemptResult(transport, canRetryRelay)
                }
            } catch (err: Exception) {
                onState("ICE falhou: " + (err.message ?: err.javaClass.simpleName))
                return AttemptResult(canRetryRelay = canRetryRelay)
            } finally {
                if (!success) transport?.close()
            }
        }

        /**
         * Local diagnostic only: collecting candidates does NOT establish a
         * session with a remote peer. Reports srflx and relay candidates without
         * claiming ICE connected; a two-phone test verifies connectivity.
         */
        fun probeIce(context: Context, endpoint: String, overrides: String = ""): String {
            val servers = IceServerConfig.load(endpoint, overrides)
            val candidates = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val gathered = CountDownLatch(1)
            var peer: PeerConnection? = null
            try {
                val factory = buildFactory(context)
                val config = PeerConnection.RTCConfiguration(servers)
                peer = factory.createPeerConnection(config,
                    object : PeerConnection.Observer {
                        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
                        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {
                            if (s == PeerConnection.IceGatheringState.COMPLETE)
                                gathered.countDown()
                        }
                        override fun onIceCandidate(candidate: IceCandidate) {
                            val type = when {
                                candidate.sdp.contains(" typ relay ") -> "relay/TURN"
                                candidate.sdp.contains(" typ srflx ") -> "srflx/STUN"
                                candidate.sdp.contains(" typ host ") -> "host/LAN"
                                else -> "outro"
                            }
                            candidates.add(type)
                        }
                        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                        override fun onAddStream(stream: MediaStream) {}
                        override fun onRemoveStream(stream: MediaStream) {}
                        override fun onDataChannel(dc: DataChannel) {}
                        override fun onRenegotiationNeeded() {}
                    }) ?: error("RTC indisponível")
                peer.createDataChannel("connect-test", DataChannel.Init())
                val offer = awaitSdpCreate(peer, true)
                setSdp(peer, offer, true)
                val complete = gathered.await(14, TimeUnit.SECONDS)
                return "Teste ICE local: " + if (!complete) {
                    "gathering excedeu 14 segundos. Candidatos: " +
                        candidates.joinToString().ifBlank { "nenhum" }
                } else {
                    "candidatos " + candidates.joinToString().ifBlank { "nenhum" } +
                        ". TURN " + if ("relay/TURN" in candidates) "autenticado"
                            else "não confirmado" +
                        ". Isso NÃO confirma conexão entre celulares."
                }
            } catch (e: Exception) {
                return "Teste ICE falhou: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                runCatching { peer?.close() }
                runCatching { peer?.dispose() }
            }
        }
    }
}
