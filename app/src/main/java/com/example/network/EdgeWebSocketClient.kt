package com.example.network

import android.net.Network
import android.os.SystemClock
import android.util.Log
import com.example.model.PlayerLiveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class EdgePingTestResult(
    val success: Boolean,
    val rttMs: Int? = null,
    val boundToSlice: Boolean = false,
    val message: String
)

/**
 * Direct OkHttp WebSocket transport for optional 5G SA MEC / Edge low-latency relay servers.
 *
 * When a 5G Low-Latency Slice [Network] is granted by Android ConnectivityManager, this client
 * binds its [OkHttpClient.Builder.socketFactory] directly to `sliceNetwork.socketFactory` so
 * live move deltas and monotonic ping/pong probes physically traverse the 5G SA slice.
 */
class EdgeWebSocketClient(
    private val scope: CoroutineScope
) {
    private var client: OkHttpClient? = null
    private var socket: WebSocket? = null
    private var pingJob: Job? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var isBoundToSlice: Boolean = false
        private set

    private var seqCounter = 0L

    fun connect(
        url: String,
        matchId: String,
        localUid: String,
        localName: String,
        sliceNetwork: Network?,
        onStatusChanged: (connected: Boolean, boundToSlice: Boolean, statusText: String) -> Unit,
        onRttMeasured: (rttMs: Int) -> Unit,
        onOpponentStateReceived: (PlayerLiveState) -> Unit,
        onRemoteFinishReceived: (winnerUid: String) -> Unit
    ) {
        disconnect()
        val trimmed = url.trim()
        if (!isValidWebSocketUrl(trimmed)) {
            onStatusChanged(false, false, "Cloud Default")
            return
        }

        val okClient = buildClient(sliceNetwork)
        client = okClient
        isBoundToSlice = sliceNetwork != null
        seqCounter = 0L

        onStatusChanged(false, isBoundToSlice, "Connecting Edge WS...")

        val request = Request.Builder().url(trimmed).build()
        socket = okClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                onStatusChanged(
                    true,
                    isBoundToSlice,
                    if (isBoundToSlice) "Edge WS (5G Slice)" else "Edge WS Connected"
                )
                val joinMsg = JSONObject().apply {
                    put("type", "join")
                    put("matchId", matchId)
                    put("uid", localUid)
                    put("name", localName)
                }
                webSocket.send(joinMsg.toString())
                startPingLoop(webSocket, matchId, localUid)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val type = obj.optString("type")
                    when (type) {
                        "pong", "ping" -> {
                            // Supports both dedicated pong responses and simple WebSocket echo servers.
                            val senderUid = obj.optString("uid")
                            if (type == "pong" || senderUid == localUid || senderUid.isBlank()) {
                                val sentNanos = obj.optLong("tNanos", 0L)
                                if (sentNanos > 0L) {
                                    val rttMs = ((SystemClock.elapsedRealtimeNanos() - sentNanos) / 1_000_000L)
                                        .toInt()
                                        .coerceAtLeast(1)
                                    onRttMeasured(rttMs)
                                }
                            } else if (type == "ping") {
                                val pong = JSONObject().apply {
                                    put("type", "pong")
                                    put("matchId", matchId)
                                    put("uid", senderUid)
                                    put("tNanos", obj.optLong("tNanos", 0L))
                                }
                                webSocket.send(pong.toString())
                            }
                        }

                        "state" -> {
                            val senderUid = obj.optString("uid")
                            if (senderUid.isNotBlank() && senderUid != localUid) {
                                val remoteMatchId = obj.optString("matchId")
                                if (remoteMatchId.isBlank() || remoteMatchId == matchId) {
                                    onOpponentStateReceived(decodePlayerState(obj))
                                }
                            }
                        }

                        "finish" -> {
                            val remoteMatchId = obj.optString("matchId")
                            val winnerUid = obj.optString("winnerUid")
                            if ((remoteMatchId.isBlank() || remoteMatchId == matchId) && winnerUid.isNotBlank()) {
                                onRemoteFinishReceived(winnerUid)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ignoring malformed edge WS frame: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                pingJob?.cancel()
                onStatusChanged(false, false, "Cloud Fallback")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                pingJob?.cancel()
                onStatusChanged(false, false, "Cloud Fallback")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                pingJob?.cancel()
                Log.w(TAG, "Edge WebSocket failure: ${t.message}")
                onStatusChanged(false, false, "Cloud Fallback")
            }
        })
    }

    fun sendPlayerState(matchId: String, state: PlayerLiveState): Boolean {
        val ws = socket ?: return false
        if (!isConnected) return false
        val payload = JSONObject().apply {
            put("type", "state")
            put("matchId", matchId)
            put("seq", ++seqCounter)
            put("uid", state.uid)
            put("name", state.name)
            put("trophies", state.trophies)
            put("waveIndex", state.waveIndex)
            put("bossHp", state.bossHp)
            put("bossMaxHp", state.bossMaxHp)
            put("totalDamage", state.totalDamage)
            put("restorationPct", state.restorationPct)
            put("boardFlat", JSONArray(state.boardFlat))
            put("skillCharges", JSONArray(state.skillCharges))
            put("lastAbility", state.lastAbility)
        }
        return ws.send(payload.toString())
    }

    fun sendFinish(matchId: String, winnerUid: String): Boolean {
        val ws = socket ?: return false
        if (!isConnected) return false
        val payload = JSONObject().apply {
            put("type", "finish")
            put("matchId", matchId)
            put("winnerUid", winnerUid)
        }
        return ws.send(payload.toString())
    }

    fun disconnect() {
        pingJob?.cancel()
        pingJob = null
        isConnected = false
        isBoundToSlice = false
        try {
            socket?.close(1000, "Session ended")
        } catch (_: Exception) {
        }
        socket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
    }

    private fun startPingLoop(ws: WebSocket, matchId: String, localUid: String) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isConnected) {
                val ping = JSONObject().apply {
                    put("type", "ping")
                    put("matchId", matchId)
                    put("uid", localUid)
                    put("tNanos", SystemClock.elapsedRealtimeNanos())
                }
                ws.send(ping.toString())
                delay(1000L)
            }
        }
    }

    private fun decodePlayerState(o: JSONObject): PlayerLiveState {
        val boardArr = o.optJSONArray("boardFlat")
        val boardFlat = if (boardArr != null) List(boardArr.length()) { boardArr.optInt(it) } else emptyList()
        val chargesArr = o.optJSONArray("skillCharges")
        val charges = if (chargesArr != null) List(chargesArr.length()) { chargesArr.optInt(it) } else listOf(0, 0, 0)
        return PlayerLiveState(
            uid = o.optString("uid"),
            name = o.optString("name", "Rival"),
            trophies = o.optInt("trophies"),
            waveIndex = o.optInt("waveIndex", 1),
            bossHp = o.optInt("bossHp", 1500),
            bossMaxHp = o.optInt("bossMaxHp", 1500).coerceAtLeast(1),
            totalDamage = o.optInt("totalDamage"),
            restorationPct = o.optInt("restorationPct"),
            boardFlat = boardFlat,
            skillCharges = charges,
            lastAbility = o.optString("lastAbility")
        )
    }

    companion object {
        private const val TAG = "EdgeWebSocketClient"

        fun isValidWebSocketUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("ws://", ignoreCase = true) ||
                trimmed.startsWith("wss://", ignoreCase = true)
        }

        private fun buildClient(sliceNetwork: Network?): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(4, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
            if (sliceNetwork != null) {
                builder.socketFactory(sliceNetwork.socketFactory)
            }
            return builder.build()
        }

        /**
         * One-shot handshake + ping probe for the Settings / Lobby "Test Edge Server" button.
         */
        suspend fun testConnection(url: String, sliceNetwork: Network?): EdgePingTestResult {
            val trimmed = url.trim()
            if (!isValidWebSocketUrl(trimmed)) {
                return EdgePingTestResult(
                    success = false,
                    message = "URL must start with ws:// or wss://"
                )
            }
            val resultDeferred = CompletableDeferred<EdgePingTestResult>()
            val okClient = buildClient(sliceNetwork)
            val bound = sliceNetwork != null
            val startNanos = SystemClock.elapsedRealtimeNanos()

            val ws = okClient.newWebSocket(
                Request.Builder().url(trimmed).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val handshakeMs = ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L)
                            .toInt()
                            .coerceAtLeast(1)
                        val ping = JSONObject().apply {
                            put("type", "ping")
                            put("matchId", "probe")
                            put("uid", "probe")
                            put("tNanos", SystemClock.elapsedRealtimeNanos())
                        }
                        webSocket.send(ping.toString())
                        // If server doesn't echo JSON ping within 1.2s, still report real handshake RTT.
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(1200L)
                            if (!resultDeferred.isCompleted) {
                                resultDeferred.complete(
                                    EdgePingTestResult(
                                        success = true,
                                        rttMs = handshakeMs,
                                        boundToSlice = bound,
                                        message = "Connected (${handshakeMs}ms handshake${if (bound) " via 5G Slice" else ""})"
                                    )
                                )
                                webSocket.close(1000, "Probe done")
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (resultDeferred.isCompleted) return
                        val rtt = try {
                            val obj = JSONObject(text)
                            val sent = obj.optLong("tNanos", 0L)
                            if (sent > 0L) ((SystemClock.elapsedRealtimeNanos() - sent) / 1_000_000L).toInt().coerceAtLeast(1)
                            else ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L).toInt().coerceAtLeast(1)
                        } catch (_: Exception) {
                            ((SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L).toInt().coerceAtLeast(1)
                        }
                        resultDeferred.complete(
                            EdgePingTestResult(
                                success = true,
                                rttMs = rtt,
                                boundToSlice = bound,
                                message = "Edge RTT: ${rtt}ms${if (bound) " (5G Slice)" else ""}"
                            )
                        )
                        webSocket.close(1000, "Probe done")
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.complete(
                                EdgePingTestResult(
                                    success = false,
                                    boundToSlice = bound,
                                    message = t.localizedMessage ?: "Connection failed"
                                )
                            )
                        }
                    }
                }
            )

            val res = withTimeoutOrNull(4500L) { resultDeferred.await() }
                ?: EdgePingTestResult(success = false, boundToSlice = bound, message = "Timed out after 4.5s")
            try {
                ws.close(1000, "Done")
            } catch (_: Exception) {
            }
            okClient.dispatcher.executorService.shutdown()
            return res
        }
    }
}
