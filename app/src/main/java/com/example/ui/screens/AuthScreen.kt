package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.AuthManager
import com.example.ui.components.EcoBrandBadge
import com.example.ui.components.GlossyButton
import com.example.ui.components.WasteCrushLogoWordmark
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueOfflinePractice: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Stay on interactive sign-in screen */ },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SidiCobalt, Color(0xFF0A3991), BattleBoardDark)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero Crest + WasteCrush Wordmark
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EcoBrandBadge()
                Spacer(Modifier.height(14.dp))

                WasteCrushLogoWordmark()

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "1v1 Tunisian Eco Match-3 Race",
                    style = MaterialTheme.typography.titleMedium,
                    color = SaffronCream,
                    textAlign = TextAlign.Center
                )
            }

            // Feature Showcase Card with Tunisian Beaches Artwork + Custom Boss Sprite
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = SurfaceWhite,
                border = BorderStroke(3.dp, DeepNavyInk),
                shadowElevation = 10.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(162.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_arena_beaches),
                            contentDescription = "Tunisian coastal arena preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, DeepNavyInk.copy(alpha = 0.78f))
                                    )
                                )
                        )
                        com.example.ui.components.BossMonsterAvatar(
                            boss = com.example.model.GameCatalog.bosses.first(),
                            modifier = Modifier
                                .size(106.dp)
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp)
                        )
                        Text(
                            text = "Purify 15 Tunisian Landmarks\n& Capture 15 Bosses",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.68f)
                                .padding(12.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FeatureRow(Icons.Default.Recycling, EcoEmerald, "Speed Match-3 1v1 combat with unlimited moves")
                        FeatureRow(Icons.Default.EmojiEvents, SaffronGold, "Climb 3 Tunisian Arenas, upgrade your 3-slot Eco-Deck")
                        FeatureRow(Icons.Default.LiveTv, SidiCobalt, "Spectate live matches, watch Friend Replays & inspect decks")
                        FeatureRow(Icons.Default.Stars, RarityEpic, "Complete Daily Quests for Eco-Coins & Boss Cards")
                    }
                }
            }

            // Google Sign-In CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMsg != null) {
                    Surface(
                        color = CarthageCoral.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CarthageCoral),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMsg ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = SaffronGold,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Connecting to WasteCrush Arena...",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                } else {
                    GlossyButton(
                        text = "Sign in with Google",
                        onClick = {
                            errorMsg = null
                            isLoading = true
                            AuthManager.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMsg = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                }
                            )
                        },
                        topColor = EcoEmeraldBright,
                        bottomColor = EcoEmerald,
                        lipColor = EcoEmeraldDark,
                        modifier = Modifier.fillMaxWidth(),
                        height = 58.dp,
                        tag = "google_sign_in_button"
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sign in with Google for live 1v1 Quick Match, Private Rooms & cloud Trophy sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = SidiSkyLight,
                    textAlign = TextAlign.Center
                )
                TextButton(
                    onClick = onContinueOfflinePractice,
                    modifier = Modifier.testTag("offline_practice_button")
                ) {
                    Text(
                        text = "Continue Offline Practice (AI Bots Only)",
                        style = MaterialTheme.typography.labelMedium,
                        color = SaffronCream
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = DeepNavyInk
        )
    }
}
