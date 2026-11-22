package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

/** How an avatar is unlocked. Everything is derived from the profile, so nothing extra is stored. */
sealed interface AvatarUnlock {
    data object Starter : AvatarUnlock
    data class Wins(val count: Int) : AvatarUnlock
    data class Trophies(val count: Int) : AvatarUnlock
    data class CaptureBoss(val bossId: String) : AvatarUnlock
}

data class AvatarDefinition(
    val id: String,
    val name: String,
    @DrawableRes val spriteRes: Int,
    val background: Color,
    val unlock: AvatarUnlock
) {
    fun isUnlocked(profile: UserProfile): Boolean = when (unlock) {
        AvatarUnlock.Starter -> true
        is AvatarUnlock.Wins -> profile.wins >= unlock.count
        is AvatarUnlock.Trophies -> profile.trophies >= unlock.count
        is AvatarUnlock.CaptureBoss -> (profile.bossLevels[unlock.bossId] ?: 0) >= 1
    }

    fun requirementText(): String = when (unlock) {
        AvatarUnlock.Starter -> "Starter avatar"
        is AvatarUnlock.Wins -> if (unlock.count == 1) "Win your first battle" else "Win ${unlock.count} battles"
        is AvatarUnlock.Trophies -> "Reach ${unlock.count} trophies"
        is AvatarUnlock.CaptureBoss -> "Capture ${GameCatalog.bossById(unlock.bossId).name}"
    }

    /** 0..1 progress towards the unlock, for the locked-avatar bar. */
    fun progress(profile: UserProfile): Float = when (unlock) {
        AvatarUnlock.Starter -> 1f
        is AvatarUnlock.Wins -> (profile.wins.toFloat() / unlock.count).coerceIn(0f, 1f)
        is AvatarUnlock.Trophies -> (profile.trophies.toFloat() / unlock.count).coerceIn(0f, 1f)
        is AvatarUnlock.CaptureBoss -> if (isUnlocked(profile)) 1f else 0f
    }
}

object AvatarCatalog {
    const val DEFAULT_ID = "av_sachet"

    private val minis = listOf(
        AvatarDefinition("av_sachet", "Sachet volant", R.drawable.enemy_sachet, Color(0xFF2563EB), AvatarUnlock.Starter),
        AvatarDefinition("av_bouteille", "Bouteillon", R.drawable.enemy_bouteille, Color(0xFF0891B2), AvatarUnlock.Starter),
        AvatarDefinition("av_gobelet", "Gobelet", R.drawable.enemy_gobelet, Color(0xFFB45309), AvatarUnlock.Wins(1)),
        AvatarDefinition("av_tesson", "Tesson", R.drawable.enemy_tesson, Color(0xFF15803D), AvatarUnlock.Trophies(150)),
        AvatarDefinition("av_canette", "Canette rouillée", R.drawable.enemy_canette, Color(0xFFCA8A04), AvatarUnlock.Wins(5)),
        AvatarDefinition("av_goutte", "Gouttelette", R.drawable.enemy_goutte, Color(0xFFDC2626), AvatarUnlock.Wins(10))
    )

    /** One avatar per boss, unlocked by capturing that boss's card. */
    private val bossAvatars = GameCatalog.bosses.map { boss ->
        AvatarDefinition("av_${boss.id}", boss.name, boss.spriteRes, boss.rarity.frameColor, AvatarUnlock.CaptureBoss(boss.id))
    }

    val all: List<AvatarDefinition> = minis + bossAvatars

    fun byId(id: String?): AvatarDefinition = all.firstOrNull { it.id == id } ?: all.first()

    fun unlockedFor(profile: UserProfile): List<AvatarDefinition> = all.filter { it.isUnlocked(profile) }

    /** The avatar to show: the chosen one if still unlocked, otherwise the default. */
    fun displayedFor(profile: UserProfile): AvatarDefinition =
        byId(profile.avatarId).takeIf { it.isUnlocked(profile) } ?: byId(DEFAULT_ID)
}
