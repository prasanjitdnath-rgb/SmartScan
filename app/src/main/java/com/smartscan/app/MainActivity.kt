package com.smartscan.app

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.init(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF64B5F6))) { App() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App() {
        val ctx = LocalContext.current
        val items by vm.items.collectAsState()
        val busy by vm.busy.collectAsState()
        var selected by remember { mutableStateOf<String?>(null) }

        val scanner = remember {
            GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true).setPageLimit(30)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build())
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.let { res ->
                    vm.save(res) { AdManager.onScanSaved(this@MainActivity) }
                }
            }
        }
        fun startScan() {
            scanner.getStartScanIntent(this@MainActivity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { Toast.makeText(ctx, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
        }

        val current = items.firstOrNull { it.name == selected }
        BackHandler(current != null) { selected = null }

        Scaffold(
            topBar = { TopAppBar(title = { Text(current?.name ?: "Smart Scan") },
                navigationIcon = { if (current != null) IconButton({ selected = null }) { Icon(Icons.Default.ArrowBack, null) } }) },
            bottomBar = { Banner() },
            floatingActionButton = { if (current == null) ExtendedFloatingActionButton(onClick = { startScan() },
                icon = { Icon(Icons.Default.CameraAlt, null) }, text = { Text("Scan") }) }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (current != null) Detail(current) else Home(items, onOpen = { selected = it.name })
                if (busy) Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Card { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text("Reading text (OCR)…") } }
                }
            }
        }
    }

    @Composable
    fun Home(items: List<ScanItem>, onOpen: (ScanItem) -> Unit) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No scans yet. Tap Scan to start.") }
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.name }) { ScanCard(it, onOpen) }
        }
    }

    @Composable
    fun ScanCard(item: ScanItem, onOpen: (ScanItem) -> Unit) {
        val ctx = LocalContext.current
        val clip = LocalClipboardManager.current
        var menu by remember { mutableStateOf(false) }
        var rename by remember { mutableStateOf(false) }
        var delete by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth().clickable { onOpen(item) }, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(item.pages.firstOrNull(), null, Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(item.date)),
                        style = MaterialTheme.typography.bodySmall)
                    Text("${item.pages.size} page(s)", style = MaterialTheme.typography.bodySmall)
                    Row {
                        IconButton({ sharePdf(ctx, item) }) { Icon(Icons.Default.Share, "Share") }
                        IconButton({ clip.setText(AnnotatedString(item.text)); Toast.makeText(ctx, "Text copied", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Default.ContentCopy, "Copy text") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Save pages as JPEG") }, {
                                    menu = false; val n = vm.saveToGallery(item)
                                    Toast.makeText(ctx, "Saved $n image(s) to Pictures/SmartScan", Toast.LENGTH_SHORT).show() })
                                DropdownMenuItem({ Text("Print") }, { menu = false; printPdf(ctx, item) })
                                DropdownMenuItem({ Text("Rename") }, { menu = false; rename = true })
                                DropdownMenuItem({ Text("Delete") }, { menu = false; delete = true })
                            }
                        }
                    }
                }
            }
        }
        if (rename) {
            var t by remember { mutableStateOf(item.name) }
            AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename") },
                text = { OutlinedTextField(t, { t = it }, singleLine = true) },
                confirmButton = { TextButton({
                    if (!vm.rename(item, t)) Toast.makeText(ctx, "Invalid or duplicate name", Toast.LENGTH_SHORT).show()
                    rename = false }) { Text("OK") } },
                dismissButton = { TextButton({ rename = false }) { Text("Cancel") } })
        }
        if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete scan?") },
            text = { Text(item.name) },
            confirmButton = { TextButton({ vm.delete(item); delete = false }) { Text("Delete") } },
            dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
    }

    @Composable
    fun Detail(item: ScanItem) {
        val ctx = LocalContext.current
        val clip = LocalClipboardManager.current
        var tab by remember { mutableIntStateOf(0) }
        val text = remember(item) { item.text }
        Column(Modifier.fillMaxSize()) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Pages") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Text (OCR)") })
            }
            if (tab == 0) LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(item.pages) { AsyncImage(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
            } else Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ clip.setText(AnnotatedString(text)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Text("Copy") }
                    OutlinedButton({ shareText(ctx, text) }) { Text("Share text") }
                }
                Spacer(Modifier.height(12.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn { item { Text(text.ifBlank { "No text found in this scan." }) } }
                }
            }
        }
    }

    @Composable
    fun Banner() {
        AndroidView(Modifier.fillMaxWidth().navigationBarsPadding(), factory = { c ->
            AdView(c).apply { setAdSize(AdSize.BANNER); adUnitId = AdManager.BANNER_ID; loadAd(AdRequest.Builder().build()) }
        })
    }
}
