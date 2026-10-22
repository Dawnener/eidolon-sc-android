package sc.eidolon.nativeapp.model

enum class DataPackageCategory(val wire: String, val label: String) {
    Conversations("conversations", "对话"),
    Photos("photos", "照片"),
    Memories("memories", "记忆"),
    Profile("profile", "制作人档案"),
    ;

    companion object {
        val order = entries.toList()

        fun fromWire(value: String): DataPackageCategory? = entries.firstOrNull { it.wire == value }
    }
}

data class DataPackageManifest(
    val format: Int,
    val categories: List<DataPackageCategory>,
    val counts: Map<String, Int> = emptyMap(),
    val exportedAt: String? = null,
)

data class DataPackageSkipped(
    val characterName: String? = null,
    val conversations: Int = 0,
)

data class DataPackageImported(
    val conversations: Int,
    val photos: Int,
    val memories: Int,
    val profile: Boolean,
    val skipped: List<DataPackageSkipped> = emptyList(),
)

/** Photos cannot be exported or imported without the conversations that own them. */
fun toggleDataPackageCategory(
    selected: Set<DataPackageCategory>,
    category: DataPackageCategory,
): Set<DataPackageCategory> {
    val next = if (category in selected) selected - category else selected + category
    return if (DataPackageCategory.Conversations !in next) {
        next - DataPackageCategory.Photos
    } else {
        next
    }
}
