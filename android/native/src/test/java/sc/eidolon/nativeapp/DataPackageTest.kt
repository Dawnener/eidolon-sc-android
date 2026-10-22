package sc.eidolon.nativeapp

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.DataPackageImportedDto
import sc.eidolon.nativeapp.data.api.DataPackageManifestDto
import sc.eidolon.nativeapp.data.api.toDomain
import sc.eidolon.nativeapp.model.DataPackageCategory
import sc.eidolon.nativeapp.model.toggleDataPackageCategory

class DataPackageTest {
    @Test
    fun photosFollowConversations() {
        val all = DataPackageCategory.entries.toSet()
        assertFalse(
            DataPackageCategory.Photos in
                toggleDataPackageCategory(all, DataPackageCategory.Conversations),
        )
        assertEquals(
            setOf(DataPackageCategory.Conversations, DataPackageCategory.Photos),
            toggleDataPackageCategory(
                setOf(DataPackageCategory.Photos),
                DataPackageCategory.Conversations,
            ),
        )
    }

    @Test
    fun mapsManifestAndImportedSummary() {
        val manifest = EidolonJson.decodeFromString<DataPackageManifestDto>(
            """{"format":2,"categories":["conversations","photos","memories"],
                "counts":{"conversations":2},"exported_at":"2026-10-22T00:00:00Z"}""",
        ).toDomain()
        assertEquals(2, manifest.format)
        assertEquals(
            listOf(
                DataPackageCategory.Conversations,
                DataPackageCategory.Photos,
                DataPackageCategory.Memories,
            ),
            manifest.categories,
        )
        assertEquals(2, manifest.counts["conversations"])

        val imported = EidolonJson.decodeFromString<DataPackageImportedDto>(
            """{"conversations":3,"photos":1,"memories":4,"profile":true,
                "skipped":[{"character_name":"七草はづき","conversations":2}]}""",
        ).toDomain()
        assertEquals(3, imported.conversations)
        assertEquals(1, imported.photos)
        assertEquals(4, imported.memories)
        assertTrue(imported.profile)
        assertEquals("七草はづき", imported.skipped.single().characterName)
    }
}
