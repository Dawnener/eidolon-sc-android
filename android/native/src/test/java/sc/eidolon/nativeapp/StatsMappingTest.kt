package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.StatsDto
import sc.eidolon.nativeapp.data.api.toDomain

class StatsMappingTest {
    @Test
    fun mapsTheStatsResponseIntoDomainModels() {
        val payload = """
            {
              "days_known": 12,
              "visits": {"current": 3, "longest": 5},
              "all_hours": [0, 1, 2],
              "heatmap": {"from": "2025-10-20", "counts": [0, 1, 0]},
              "affinity": [{"tier": "close", "characters": ["mano"]}],
              "achievements": {"stages": 4, "complete": 2},
              "range": {
                "span": "30d",
                "turns": 42,
                "by_hour": [1, 0, 3],
                "characters": [
                  {"character_id": "mano", "turns": 30, "days_known": 9, "call_seconds": 125.5}
                ],
                "calls": {"count": 2, "seconds": 125.5},
                "gifts": {"given": 3, "liked": 2, "most": {"character_id": "mano", "given": 2}}
              }
            }
        """.trimIndent()

        val stats = EidolonJson.decodeFromString<StatsDto>(payload).toDomain()

        assertEquals(12, stats.daysKnown)
        assertEquals(3, stats.visits.current)
        assertEquals(5, stats.visits.longest)
        assertEquals("2025-10-20", stats.heatmap.from)
        assertEquals(listOf(0, 1, 0), stats.heatmap.counts)
        assertEquals("close", stats.affinity?.single()?.tier)
        assertEquals(4, stats.achievements?.stages)
        assertEquals("30d", stats.range.span)
        assertEquals(42, stats.range.turns)
        assertEquals(30, stats.range.characters.single().turns)
        assertEquals(125.5, stats.range.characters.single().callSeconds, 0.0)
        assertEquals(2, stats.range.calls.count)
        assertEquals(3, stats.range.gifts?.given)
        assertEquals("mano", stats.range.gifts?.most?.characterId)
    }

    @Test
    fun keepsAffinityAndGiftsOffAsNull() {
        val payload = """
            {
              "days_known": 1,
              "visits": {"current": 0, "longest": 0},
              "all_hours": [],
              "heatmap": {"from": "2026-10-19", "counts": []},
              "affinity": null,
              "achievements": null,
              "range": {
                "span": "all",
                "turns": 0,
                "by_hour": [],
                "characters": [],
                "calls": {"count": 0, "seconds": 0},
                "gifts": null
              }
            }
        """.trimIndent()

        val stats = EidolonJson.decodeFromString<StatsDto>(payload).toDomain()

        assertNull(stats.affinity)
        assertNull(stats.achievements)
        assertNull(stats.range.gifts)
    }
}
