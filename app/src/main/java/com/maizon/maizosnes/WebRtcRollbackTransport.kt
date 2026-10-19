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
            repeat(if (host) 1 else 7) { attempt ->
                var sock: WssRelaySocket? = null
                try {
                    sock = WssRelaySocket.connect(endpoint)
                    sock.soTimeout = if (host) 6500 else 5000
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
                        sock.soTimeout = 25000
                        return sock
                    }
                    throw java.io.IOException("Sinalização WSS: " + answer)
                } catch (err: Exception) {
                    sock?.close()
                    last = err
                    if (!host && attempt < 6) Thread.sleep(300)
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
        fun attempt(context: Context, endpoint: String, roomId: String,
                    hosting: Boolean, onState: (String) -> Unit):
            WebRtcRollbackTransport? {
            val signal = try { openSignaling(endpoint, roomId, hosting) }
                catch (e: Exception) {
                    onState("WebRTC sinalização indisponível: " + e.message)
                    return null
                }
            var factory: PeerConnectionFactory? = null
            var transport: WebRtcRollbackTransport? = null
            try {
                signal.use { ws ->
                    factory = buildFactory(context)
                    val gather = CountDownLatch(1)
                    val open = CountDownLatch(1)
                    val packets = ArrayBlockingQueue<ByteArray>(384)
                    var connection: WebRtcRollbackTransport? = null
                    val ice = listOf(
                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
                    )
                    // Public TURN is not assumed to be usable without credentials.
                    val config = PeerConnection.RTCConfiguration(ice)
                    val peer = factory!!.createPeerConnection(config,
                        object : PeerConnection.Observer {
                            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                                connection?.iceState = s.name.lowercase()
                                onState("ICE: " + s.name.lowercase())
                            }
                            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {
                                if (s == PeerConnection.IceGatheringState.COMPLETE)
                                    gather.countDown()
                            }
                            override fun onIceCandidate(candidate: IceCandidate) {}
                            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                            override fun onAddStream(stream: MediaStream) {}
                            override fun onRemoveStream(stream: MediaStream) {}
                            override fun onDataChannel(dc: DataChannel) {
                                connection?.bind(dc)
                            }
                            override fun onRenegotiationNeeded() {}
                        }) ?: error("RTC PeerConnection indisponível")
                    transport = WebRtcRollbackTransport(peer, packets, open)
                    connection = transport
                    if (hosting) {
                        val settings = DataChannel.Init()
                        settings.ordered = false
                        settings.maxRetransmits = 0
                        val dc = peer.createDataChannel("gekko-udp", settings)
                            ?: error("Não criou DataChannel")
                        transport!!.bind(dc)
                    }
                    val input = DataInputStream(ws.getInputStream())
                    val output = DataOutputStream(ws.getOutputStream())
                    if (hosting) {
                        val offer = awaitSdpCreate(peer, true)
                        setSdp(peer, offer, true)
                        require(gather.await(10, TimeUnit.SECONDS)) { "ICE gathering excedeu prazo" }
                        sendSdp(output, peer.localDescription ?: offer)
                        setSdp(peer, receiveSdp(input, SessionDescription.Type.ANSWER), false)
                    } else {
                        setSdp(peer, receiveSdp(input, SessionDescription.Type.OFFER), false)
                        val answer = awaitSdpCreate(peer, false)
                        setSdp(peer, answer, true)
                        require(gather.await(10, TimeUnit.SECONDS)) { "ICE gathering excedeu prazo" }
                        sendSdp(output, peer.localDescription ?: answer)
                    }
                    val ready = open.await(12, TimeUnit.SECONDS) &&
                        transport!!.channel?.state() == DataChannel.State.OPEN
                    // Both peers commit to rollback only after mutual success.
                    output.writeBoolean(ready)
                    output.flush()
                    val partnerReady = input.readBoolean()
                    if (!ready || !partnerReady) {
                        onState("ICE não conectou: usando Lockstep TCP.")
                        return null
                    }
                    onState("ICE connected • WebRTC DataChannel não ordenado ativo")
                    return transport
                }
            } catch (e: Exception) {
                onState("WebRTC indisponível: " + (e.message ?: "ICE/SDP"))
                transport?.close()
                return null
            } finally {
                // The data channel owns native state; the process keeps the
                // factory as a singleton until a future session.
                // Do not dispose the peer factory while an active peer exists.
            }
        }
    }
}
