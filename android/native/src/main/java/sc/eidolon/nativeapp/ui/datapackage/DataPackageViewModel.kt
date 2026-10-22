package sc.eidolon.nativeapp.ui.datapackage

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import sc.eidolon.nativeapp.core.EidolonJson
import sc.eidolon.nativeapp.data.api.ApiError
import sc.eidolon.nativeapp.data.api.DataPackageManifestDto
import sc.eidolon.nativeapp.data.api.EidolonApi
import sc.eidolon.nativeapp.data.api.toDomain
import sc.eidolon.nativeapp.model.DataPackageCategory
import sc.eidolon.nativeapp.model.DataPackageImported
import sc.eidolon.nativeapp.model.DataPackageManifest
import sc.eidolon.nativeapp.model.toggleDataPackageCategory
import sc.eidolon.nativeapp.model.untilText
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

private const val MAX_PACKAGE_BYTES = 200L * 1024 * 1024
private const val MAX_MANIFEST_BYTES = 64 * 1024
private const val FORMAT = 2

data class DataPackageUiState(
    val exportCategories: Set<DataPackageCategory> = DataPackageCategory.entries.toSet(),
    val importCategories: Set<DataPackageCategory> = emptySet(),
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val reading: Boolean = false,
    val pickedName: String? = null,
    val manifest: DataPackageManifest? = null,
    val imported: DataPackageImported? = null,
    val notice: String? = null,
    val error: String? = null,
)

class DataPackageViewModel(
    application: Application,
    private val api: EidolonApi,
) : AndroidViewModel(application) {
    var state by mutableStateOf(DataPackageUiState())
        private set

    private val resolver: ContentResolver get() = getApplication<Application>().contentResolver
    private var importUri: Uri? = null

    fun toggleExport(category: DataPackageCategory) {
        state = state.copy(
            exportCategories = toggle(state.exportCategories, category),
            error = null,
        )
    }

    fun toggleImport(category: DataPackageCategory) {
        state = state.copy(
            importCategories = toggle(state.importCategories, category),
            error = null,
        )
    }

    fun export(destination: Uri) {
        val categories = state.exportCategories
        if (categories.isEmpty() || state.exporting) return
        state = state.copy(exporting = true, error = null, notice = null)
        viewModelScope.launch {
            try {
                val ordered = DataPackageCategory.order.filter { it in categories }
                api.checkDataPackageExport(ordered)
                api.exportDataPackage(ordered) { input ->
                    val output = resolver.openOutputStream(destination)
                        ?: error("无法打开保存位置")
                    output.use { out -> input.copyTo(out) }
                }
                state = state.copy(exporting = false, notice = "数据包已导出")
            } catch (error: Exception) {
                state = state.copy(exporting = false, error = packageError(error))
            }
        }
    }

    fun readImport(uri: Uri) {
        if (state.reading || state.importing) return
        importUri = uri
        state = state.copy(
            reading = true,
            error = null,
            notice = null,
            imported = null,
            manifest = null,
            importCategories = emptySet(),
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { readManifest(uri) }
                val categories = result.manifest.categories.toSet().let {
                    if (DataPackageCategory.Conversations !in it) {
                        it - DataPackageCategory.Photos
                    } else {
                        it
                    }
                }
                state = state.copy(
                    reading = false,
                    pickedName = result.name,
                    manifest = result.manifest,
                    importCategories = categories,
                )
            } catch (error: Exception) {
                state = state.copy(reading = false, error = packageError(error))
            }
        }
    }

    fun importSelected() {
        val uri = importUri ?: return
        startImport(uri)
    }

    private fun startImport(uri: Uri) {
        val categories = state.importCategories
        if (categories.isEmpty() || state.importing) return
        state = state.copy(importing = true, error = null, notice = null, imported = null)
        viewModelScope.launch {
            try {
                val size = querySize(uri)
                if (size != null && size > MAX_PACKAGE_BYTES) {
                    throw DataPackageFailure("too_large")
                }
                val body = streamBody(uri, size)
                val ordered = DataPackageCategory.order.filter { it in categories }
                val imported = api.importDataPackage(ordered, body)
                state = state.copy(
                    importing = false,
                    imported = imported,
                    notice = "导入完成",
                )
            } catch (error: Exception) {
                state = state.copy(importing = false, error = packageError(error))
            }
        }
    }

    fun consumeNotice() {
        state = state.copy(notice = null)
    }

    private fun toggle(
        selected: Set<DataPackageCategory>,
        category: DataPackageCategory,
    ): Set<DataPackageCategory> = toggleDataPackageCategory(selected, category)

    private fun readManifest(uri: Uri): ManifestResult {
        val name = displayName(uri)
        val size = querySize(uri)
        if (size != null && size > MAX_PACKAGE_BYTES) throw DataPackageFailure("too_large")
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                val entry = zip.nextEntry ?: throw DataPackageFailure("broken")
                if (entry.name != "manifest.json") throw DataPackageFailure("broken")
                val bytes = readLimited(zip, MAX_MANIFEST_BYTES)
                val manifest = EidolonJson.decodeFromString<DataPackageManifestDto>(
                    bytes.toString(Charsets.UTF_8),
                ).toDomain()
                if (manifest.format > FORMAT) throw DataPackageFailure("too_new")
                if (manifest.categories.isEmpty()) throw DataPackageFailure("broken")
                return ManifestResult(name ?: "数据包", manifest)
            }
        }
        throw DataPackageFailure("broken")
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit + 1)
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        if (total > limit) throw DataPackageFailure("broken")
        return buffer.copyOf(total)
    }

    private fun displayName(uri: Uri): String? = query(uri) { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

    private fun querySize(uri: Uri): Long? = query(uri) { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index)
        else null
    }

    private fun <T> query(uri: Uri, read: (android.database.Cursor) -> T): T? =
        runCatching {
            resolver.query(uri, null, null, null, null)?.use(read)
        }.getOrNull()

    private fun streamBody(uri: Uri, size: Long?): RequestBody = object : RequestBody() {
        override fun contentType() = "application/zip".toMediaType()

        override fun contentLength(): Long = size ?: -1

        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(uri) ?: error("无法读取数据包")
            input.use { sink.writeAll(it.source()) }
        }
    }

    private data class ManifestResult(val name: String, val manifest: DataPackageManifest)
}

private class DataPackageFailure(val code: String) : Exception(code)

private fun packageError(error: Exception): String {
    val api = error as? ApiError
    return when {
        api?.code == "too_large" || api?.status == 413 ->
            "数据包太大了（最大 200 MB），可以不带照片重新导出。"
        api?.code == "too_new" -> "这个数据包来自更新的版本，请先升级部署。"
        api?.code == "busy" -> "已经有一个数据包在导入或导出，请稍后再试。"
        api?.code == "used_up" -> {
            val retry = api.retryAt?.let { untilText(it, Instant.now(), ZoneId.systemDefault()) }
            "今天的导入次数已经用完了（每天 3 次）${retry?.let { "，$it 后恢复" }.orEmpty()}。"
        }
        api?.code == "broken" -> "这不是一个能用的数据包。"
        error is DataPackageFailure -> when (error.code) {
            "too_large" -> "数据包太大了（最大 200 MB）。"
            "too_new" -> "这个数据包来自更新的版本，请先升级部署。"
            else -> "这不是一个能用的数据包。"
        }
        else -> api?.message ?: error.message ?: "操作失败"
    }
}
