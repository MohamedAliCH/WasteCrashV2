package com.example

import com.example.model.AvatarCatalog
import com.example.model.GameCatalog
import com.example.model.TunisiaGeo
import com.example.model.UserProfile
import org.junit.Assert.*
import org.junit.Test

class ProfileAndMapTest {

    @Test
    fun `starter profile unlocks starters and owned bosses only`() {
        val p = UserProfile(wins = 0, trophies = 0, bossLevels = mapOf("boss_b1" to 1, "boss_b2" to 1))
        val ids = AvatarCatalog.unlockedFor(p).map { it.id }.toSet()
        assertEquals(setOf("av_sachet", "av_bouteille", "av_boss_b1", "av_boss_b2"), ids)
    }

    @Test
    fun `wins trophies and captures unlock avatars`() {
        val p = UserProfile(wins = 5, trophies = 160, bossLevels = mapOf("boss_d5" to 1))
        val ids = AvatarCatalog.unlockedFor(p).map { it.id }.toSet()
        assertTrue("av_gobelet" in ids)   // 1 win
        assertTrue("av_canette" in ids)   // 5 wins
        assertTrue("av_tesson" in ids)    // 150 trophies
        assertFalse("av_goutte" in ids)   // needs 10 wins
        assertTrue("av_boss_d5" in ids)   // captured Roi des ordures
        assertEquals(0.5f, AvatarCatalog.byId("av_goutte").progress(p))
    }

    @Test
    fun `a chosen avatar that is locked falls back to the default`() {
        val p = UserProfile(avatarId = "av_boss_d5", bossLevels = emptyMap())
        assertEquals(AvatarCatalog.DEFAULT_ID, AvatarCatalog.displayedFor(p).id)
        assertEquals(21, AvatarCatalog.all.size)
        assertEquals(AvatarCatalog.all.size, AvatarCatalog.all.map { it.id }.distinct().size)
    }

    @Test
    fun `real outline and every place pin fit inside the map window`() {
        assertEquals(3, TunisiaGeo.RINGS.size) // mainland, Djerba, Kerkennah
        for (ring in TunisiaGeo.RINGS) {
            var i = 0
            while (i < ring.size) {
                val (x, y) = TunisiaGeo.project(ring[i], ring[i + 1])
                assertTrue(x in 0f..1f && y in 0f..1f)
                i += 2
            }
        }
        for (place in GameCatalog.allPlaces) {
            assertTrue(place.id, place.mapX in 0f..1f && place.mapY in 0f..1f)
        }
        // North is up, Sahara is down: Ichkeul (north) above Tataouine (south)
        assertTrue(GameCatalog.placeById("place_ichkeul").mapY < GameCatalog.placeById("place_tataouine").mapY)
        assertTrue(TunisiaGeo.ASPECT in 0.4f..0.7f)
    }
}
