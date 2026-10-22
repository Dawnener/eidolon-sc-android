package sc.eidolon.nativeapp.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sc.eidolon.nativeapp.model.DataPackageCategory
import sc.eidolon.nativeapp.model.DataPackageImported
import sc.eidolon.nativeapp.model.DataPackageManifest
import sc.eidolon.nativeapp.model.DataPackageSkipped

@Serializable
data class DataPackageManifestDto(
    val format: Int = 0,
    val categories: List<String> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    @SerialName("exported_at") val exportedAt: String? = null,
)

fun DataPackageManifestDto.toDomain(): DataPackageManifest = DataPackageManifest(
    format = format,
    categories = categories.mapNotNull(DataPackageCategory::fromWire),
    counts = counts,
    exportedAt = exportedAt,
)

@Serializable
data class DataPackageSkippedDto(
    @SerialName("character_name") val characterName: String? = null,
    val conversations: Int = 0,
)

@Serializable
data class DataPackageImportedDto(
    val conversations: Int = 0,
    val photos: Int = 0,
    val memories: Int = 0,
    val profile: Boolean = false,
    val skipped: List<DataPackageSkippedDto> = emptyList(),
)

fun DataPackageImportedDto.toDomain(): DataPackageImported = DataPackageImported(
    conversations = conversations,
    photos = photos,
    memories = memories,
    profile = profile,
    skipped = skipped.map { DataPackageSkipped(it.characterName, it.conversations) },
)
