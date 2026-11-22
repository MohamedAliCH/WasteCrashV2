package com.example.data

import com.example.engine.Match3Engine
import com.example.engine.MatchTelemetry
import com.example.model.GameCatalog
import com.example.model.QuestDefinition
import com.example.model.UserProfile
import kotlin.random.Random

/**
 * Pure profile-update rules shared by guest mode and the Firestore transactions, so both paths
 * give identical results and can be unit-tested without Firebase.
 */
object RewardRules {
    const val TROPHIES_WIN = 30
    const val TROPHIES_LOSS = -15
    const val MAX_BOSS_LEVEL = 10

    fun coinsFor(won: Boolean, arenaId: Int): Int = if (won) 95 + arenaId * 30 else 30 + arenaId * 10

    fun matchResult(profile: UserProfile, telemetry: MatchTelemetry): Pair<UserProfile, MatchRewardOutcome> {
        val trophyDelta = if (telemetry.won) TROPHIES_WIN else TROPHIES_LOSS
        val newTrophies = (profile.trophies + trophyDelta).coerceAtLeast(0)
        val coinsEarned = coinsFor(telemetry.won, telemetry.arenaId)

        val updatedLevels = profile.bossLevels.toMutableMap()
        val updatedCopies = profile.bossCopies.toMutableMap()
        var capturedBossId: String? = null
        if (telemetry.won) {
            capturedBossId = telemetry.bossId
            if ((updatedLevels[telemetry.bossId] ?: 0) == 0) {
                updatedLevels[telemetry.bossId] = 1
                updatedCopies[telemetry.bossId] = updatedCopies[telemetry.bossId] ?: 0
            } else {
                updatedCopies[telemetry.bossId] = (updatedCopies[telemetry.bossId] ?: 0) + 1
            }
        }

        val updatedQuests = Match3Engine.evaluateQuestUpdates(profile.questProgress, telemetry)
        val newlyCompleted = GameCatalog.quests.filter { q ->
            val before = profile.questProgress[q.id] ?: 0
            val after = updatedQuests[q.id] ?: 0
            before < q.target && after >= q.target
        }.map { it.id }

        val isFirstTimePurified = telemetry.won &&
            telemetry.placeId.isNotBlank() &&
            telemetry.placeId !in profile.purifiedPlaceIds
        val updatedPurifiedPlaces = if (isFirstTimePurified) {
            (profile.purifiedPlaceIds + telemetry.placeId).distinct().take(20)
        } else {
            profile.purifiedPlaceIds
        }

        val updated = profile.copy(
            trophies = newTrophies,
            ecoCoins = profile.ecoCoins + coinsEarned,
            wins = profile.wins + if (telemetry.won) 1 else 0,
            losses = profile.losses + if (telemetry.won) 0 else 1,
            bossLevels = updatedLevels,
            bossCopies = updatedCopies,
            questProgress = updatedQuests,
            purifiedPlaceIds = updatedPurifiedPlaces
        )
        // Real delta after the 0-floor, so the UI never shows "-15" when trophies were already 0.
        val outcome = MatchRewardOutcome(
            trophyDelta = newTrophies - profile.trophies,
            coinsEarned = coinsEarned,
            capturedBossId = capturedBossId,
            updatedQuestProgress = updatedQuests,
            newlyCompletedQuestIds = newlyCompleted,
            newlyPurifiedPlaceId = if (isFirstTimePurified) telemetry.placeId else null
        )
        return updated to outcome
    }

    fun upgradeBoss(profile: UserProfile, bossId: String): Pair<UserProfile, Int> {
        val boss = GameCatalog.bossById(bossId)
        val currentLevel = profile.bossLevels[bossId] ?: 0
        require(currentLevel >= 1) { "Capture this Boss first to unlock upgrades." }
        require(currentLevel < MAX_BOSS_LEVEL) { "Boss is already at maximum Level $MAX_BOSS_LEVEL." }
        val copiesNeeded = GameCatalog.copiesNeededForNextLevel(currentLevel, boss.rarity)
        val coinsNeeded = GameCatalog.coinsNeededForNextLevel(currentLevel, boss.rarity)
        val currentCopies = profile.bossCopies[bossId] ?: 0
        require(currentCopies >= copiesNeeded) { "Need $copiesNeeded copies (have $currentCopies)." }
        require(profile.ecoCoins >= coinsNeeded) { "Need $coinsNeeded Eco-Coins." }
        val newLevel = currentLevel + 1
        val updated = profile.copy(
            bossLevels = profile.bossLevels + (bossId to newLevel),
            bossCopies = profile.bossCopies + (bossId to currentCopies - copiesNeeded),
            ecoCoins = profile.ecoCoins - coinsNeeded
        )
        return updated to newLevel
    }

    fun claimQuest(profile: UserProfile, quest: QuestDefinition, random: Random = Random.Default): Pair<UserProfile, QuestClaimOutcome> {
        val currentProgress = profile.questProgress[quest.id] ?: 0
        require(currentProgress >= quest.target) { "Quest objective not completed yet." }
        require(profile.questClaimed[quest.id] != true) { "Quest reward already claimed." }

        val arena = GameCatalog.arenaForTrophies(profile.trophies)
        val pool = GameCatalog.cumulativeBossPoolForArena(arena.id)
        val rarityMatches = pool.filter { it.rarity == quest.rewardRarity }.ifEmpty {
            GameCatalog.bosses.filter { it.rarity == quest.rewardRarity }.ifEmpty { pool }
        }
        val rewardedBoss = rarityMatches.random(random)

        val updatedLevels = profile.bossLevels.toMutableMap()
        val updatedCopies = profile.bossCopies.toMutableMap()
        if ((updatedLevels[rewardedBoss.id] ?: 0) == 0) {
            updatedLevels[rewardedBoss.id] = 1
            updatedCopies[rewardedBoss.id] = (updatedCopies[rewardedBoss.id] ?: 0) + (quest.cardRewardCount - 1).coerceAtLeast(0)
        } else {
            updatedCopies[rewardedBoss.id] = (updatedCopies[rewardedBoss.id] ?: 0) + quest.cardRewardCount
        }

        val updated = profile.copy(
            ecoCoins = profile.ecoCoins + quest.coinReward,
            bossLevels = updatedLevels,
            bossCopies = updatedCopies,
            questClaimed = profile.questClaimed + (quest.id to true)
        )
        return updated to QuestClaimOutcome(
            coinsAwarded = quest.coinReward,
            rewardedBossId = rewardedBoss.id,
            cardsAwarded = quest.cardRewardCount
        )
    }
}
