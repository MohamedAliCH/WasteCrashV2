package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RoomCodeGenerator
import com.example.network.EdgePingTestResult
import com.example.network.EdgeWebSocketClient
import com.example.network.RadioLinkSnapshot
import com.example.network.SliceStatus
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Interactive 1v1 Multiplayer Launch Sheet:
 * - Shows live Android 5G SA radio & latency slice status
 * - Quick Match (Online Trophy Queue)
 * - Private Room Code Lobby (Create 6-char room or Join by code)
 * - Optional Edge / MEC WebSocket URL configuration & live ping probe
 * - Explicit Practice vs AI Bot & Google Sign-In prompt when offline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerLobbySheet(
    isGuest: Boolean,
    linkState: RadioLinkSnapshot,
    initialEdgeUrl: String,
    activeSliceNetwork: android.net.Network?,
    onDismiss: () -> Unit,
    onQuickMatch: (edgeUrl: String) -> Unit,
    onCreatePrivateRoom: (edgeUrl: String) -> Unit,
    onJoinPrivateRoom: (roomCode: String) -> Unit,
    onPracticeBot: () -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onSaveEdgeUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var joinCode by remember { mutableStateOf("") }
    var edgeUrl by remember(initialEdgeUrl) { mutableStateOf(initialEdgeUrl) }
    var showEdgeConfig by remember { mutableStateOf(initialEdgeUrl.isNotBlank()) }
    var pingTesting by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<EdgePingTestResult?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBgPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = SaffronGold) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Live 5G SA Link Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1v1 MULTIPLAYER ARENA",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                    Text(
                        text = linkState.statusNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = bearerAccentColor(linkState.bearer)
                    )
                }
                HomeNetworkBadge(
                    link = linkState,
                    hasEdgeUrl = EdgeWebSocketClient.isValidWebSocketUrl(edgeUrl),
                    onClick = { showEdgeConfig = !showEdgeConfig }
                )
            }

            if (isGuest) {
                // Clear sign-in gate for real 2-device online multiplayer
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.8.dp, SaffronGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = SaffronGold)
                            Text(
                                text = "Offline Practice Mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Sign in with Google to pair two real devices over Firebase Quick Match or Private Room Codes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedOnDark
                        )
                        GlossyButton(
                            text = "Sign in with Google (Online 1v1)",
                            onClick = onRequestGoogleSignIn,
                            icon = Icons.Default.Login,
                            topColor = CeramicBlueTop,
                            bottomColor = CeramicBlueBottom,
                            lipColor = DeepNavyInk,
                            height = 50.dp,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "lobby_google_signin_button"
                        )
                    }
                }
            } else {
                // 1. Quick Match Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.8.dp, LimePlayTop.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = LimePlayTop)
                            Column {
                                Text(
                                    text = "Online Quick Match",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Trophy-matched 1v1 queue via Firebase Firestore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedOnDark
                                )
                            }
                        }
                        GlossyButton(
                            text = "FIND ONLINE RIVAL",
                            onClick = {
                                onSaveEdgeUrl(edgeUrl)
                                onQuickMatch(edgeUrl.trim())
                            },
                            icon = Icons.Default.Public,
                            topColor = LimePlayTop,
                            bottomColor = LimePlayBottom,
                            lipColor = LimePlayLip,
                            height = 52.dp,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "lobby_quick_match_button"
                        )
                    }
                }

                // 2. Private Room Code Lobby (Ideal for 2-Phone 5G SA Live Demos)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = DarkSurfaceCard,
                    border = BorderStroke(1.8.dp, SaffronGold.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = SaffronGold)
                            Column {
                                Text(
                                    text = "Private 1v1 Room Code",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Instant deterministic pairing for 2 phones side-by-side",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedOnDark
                                )
                            }
                        }

                        GlossyButton(
                            text = "CREATE PRIVATE ROOM",
                            onClick = {
                                onSaveEdgeUrl(edgeUrl)
                                onCreatePrivateRoom(edgeUrl.trim())
                            },
                            icon = Icons.Default.AddCircleOutline,
                            topColor = Color(0xFFFFD54F),
                            bottomColor = SaffronGold,
                            lipColor = SaffronGoldDark,
                            height = 48.dp,
                            modifier = Modifier.fillMaxWidth(),
                            tag = "lobby_create_room_button"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = RoomCodeGenerator.normalize(it) },
                                placeholder = { Text("Room Code (e.g. TN4K9P)", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("lobby_room_code_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = SaffronGold,
                                    unfocusedBorderColor = DarkBorderSubtle,
                                    focusedPlaceholderColor = TextMutedOnDark,
                                    unfocusedPlaceholderColor = TextMutedOnDark
                                )
                            )
                            Button(
                                onClick = { onJoinPrivateRoom(joinCode) },
                                enabled = RoomCodeGenerator.isValid(joinCode),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CeramicBlueTop,
                                    contentColor = DeepNavyInk
                                ),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("lobby_join_room_button")
                            ) {
                                Text("JOIN", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                // 3. Optional 5G SA Edge / MEC WebSocket Relay Config
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, DarkBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEdgeConfig = !showEdgeConfig }
                                .testTag("lobby_toggle_edge_config"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF22D3EE), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Optional Edge / MEC WebSocket (5G SA)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = if (showEdgeConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = SidiSkyLight
                            )
                        }

                        AnimatedVisibility(visible = showEdgeConfig) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Leave blank for zero-setup Firebase Cloud sync, or enter your MEC WebSocket relay URL (ws:// or wss://) to bind sockets to the 5G SA slice.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMutedOnDark
                                )
                                OutlinedTextField(
                                    value = edgeUrl,
                                    onValueChange = {
                                        edgeUrl = it.take(200)
                                        pingResult = null
                                    },
                                    placeholder = { Text("ws://10.45.0.1:8080/ws", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lobby_edge_url_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF22D3EE),
                                        unfocusedBorderColor = DarkBorderSubtle
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSaveEdgeUrl(edgeUrl)
                                            pingTesting = true
                                            pingResult = null
                                            scope.launch {
                                                pingResult = EdgeWebSocketClient.testConnection(edgeUrl, activeSliceNetwork)
                                                pingTesting = false
                                            }
                                        },
                                        enabled = !pingTesting && EdgeWebSocketClient.isValidWebSocketUrl(edgeUrl),
                                        border = BorderStroke(1.2.dp, Color(0xFF22D3EE)),
                                        modifier = Modifier.testTag("lobby_edge_ping_button")
                                    ) {
                                        if (pingTesting) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF22D3EE))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (pingTesting) "Testing..." else "Ping Edge",
                                            color = Color(0xFF22D3EE),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (edgeUrl.isNotBlank()) {
                                        TextButton(onClick = {
                                            edgeUrl = ""
                                            pingResult = null
                                            onSaveEdgeUrl("")
                                        }) {
                                            Text("Clear", color = TextMutedOnDark)
                                        }
                                    }
                                    pingResult?.let { res ->
                                        Text(
                                            text = res.message,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (res.success) LimePlayTop else CarthageCoral,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Solo Practice vs AI Bot
            OutlinedButton(
                onClick = onPracticeBot,
                border = BorderStroke(1.2.dp, DarkBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("lobby_practice_bot_button")
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = SidiSkyLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Solo Practice vs AI Rival", color = TextOnDark, fontWeight = FontWeight.Bold)
            }

            val sliceNote = if (linkState.sliceStatus == SliceStatus.ACTIVE) {
                "Android 5G Low-Latency Slice (NET_CAPABILITY_PRIORITIZE_LATENCY) is active."
            } else {
                "Real-time RTT & jitter telemetry will appear live in the battle HUD."
            }
            Text(
                text = sliceNote,
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedOnDark
            )
        }
    }
}
