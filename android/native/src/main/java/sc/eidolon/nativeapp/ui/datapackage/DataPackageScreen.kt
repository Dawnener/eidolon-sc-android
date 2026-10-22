package sc.eidolon.nativeapp.ui.datapackage

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import sc.eidolon.nativeapp.AppContainer
import sc.eidolon.nativeapp.model.DataPackageCategory
import sc.eidolon.nativeapp.model.DataPackageImported
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPackageScreen(container: AppContainer, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: DataPackageViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DataPackageViewModel(app, container.api) }
        },
    )
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }
    val createPackage = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(vm::export) }
    val openPackage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::readImport) }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice)
        vm.consumeNotice()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                },
                title = { Text("数据包") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "把对话、照片、记忆和制作人档案打包带走，或者在另一台服务器上导入。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "好感度、礼物、成就、每日任务、登录方式和本机保存的自带密钥不在数据包里。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("导出") {
                Text("选择要导出的内容", style = MaterialTheme.typography.bodyMedium)
                DataPackageCategory.order.forEach { category ->
                    CategoryRow(
                        category = category,
                        checked = category in state.exportCategories,
                        enabled = category != DataPackageCategory.Photos ||
                            DataPackageCategory.Conversations in state.exportCategories,
                        onCheckedChange = { vm.toggleExport(category) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        createPackage.launch("eidolon-data-package-${LocalDate.now()}.zip")
                    },
                    enabled = state.exportCategories.isNotEmpty() && !state.exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(if (state.exporting) "正在导出…" else "导出数据包")
                }
            }

            SectionCard("导入") {
                Text(
                    "选择 .zip 数据包；导入前会先读取清单，让你选择要恢复的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        openPackage.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    },
                    enabled = !state.reading && !state.importing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.reading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(if (state.reading) "正在读取数据包…" else "选择数据包")
                }
                state.manifest?.let { manifest ->
                    Text(
                        "文件：${state.pickedName.orEmpty()}（格式 ${manifest.format}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("选择要导入的内容", style = MaterialTheme.typography.bodyMedium)
                    manifest.categories.forEach { category ->
                        CategoryRow(
                            category = category,
                            checked = category in state.importCategories,
                            enabled = category != DataPackageCategory.Photos ||
                                DataPackageCategory.Conversations in state.importCategories,
                            onCheckedChange = { vm.toggleImport(category) },
                        )
                    }
                    Button(
                        onClick = vm::importSelected,
                        enabled = state.importCategories.isNotEmpty() && !state.importing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(if (state.importing) "正在导入…" else "导入所选内容")
                    }
                }
                state.imported?.let { ImportedSummary(it) }
            }

            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CategoryRow(
    category: DataPackageCategory,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            enabled = enabled,
        )
        Text(category.label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportedSummary(imported: DataPackageImported) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("上次导入结果", style = MaterialTheme.typography.bodyMedium)
        Text(
            "对话 ${imported.conversations} 段 · 照片 ${imported.photos} 张 · 记忆 ${imported.memories} 条" +
                if (imported.profile) " · 档案已恢复" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        imported.skipped.forEach { skipped ->
            Text(
                "跳过 ${skipped.characterName ?: "未知角色"}：${skipped.conversations} 段对话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
