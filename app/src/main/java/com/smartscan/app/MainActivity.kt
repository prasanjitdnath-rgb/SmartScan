package com.smartscan.app

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF64B5F6))) { App() }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App() {
        val ctx = LocalContext.current
        val scans by vm.items.collectAsState()
        val busy by vm.busy.collectAsState()
        var selected by remember { mutableStateOf<String?>(null) }

        val scanner = remember {
            GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(30)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
            )
        }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
                if (res != null) {
                    vm.save(res) { AdManager.onScanSaved(this@MainActivity) }
                }
            }
        }
        val startScan: () -> Unit = {
            scanner.getStartScanIntent(this@MainActivity)
                .addOnSuccessListener { sender ->
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { e ->
                    Toast.makeText(ctx, "Scanner unavailable: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        val current: ScanItem? = scans.firstOrNull { it.name == selected }
        BackHandler(enabled = current != null) { selected = null }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current?.name ?: "Smart Scan") },
                    navigationIcon = {
                        if (current != null) {
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            },
            bottomBar = { Banner() },
            floatingActionButton = {
                if (current == null) {
                    ExtendedFloatingActionButton(
                        onClick = startScan,
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        text = { Text("Scan") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (current != null) {
                    Detail(current)
                } else {
                    Home(scans) { selected = it.name }
                }
                if (busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(28.dp))
                                Spacer(Modifier.width(16.dp))
                                Text("Reading text (OCR)...")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Home(list: List<ScanItem>, onOpen: (ScanItem) -> Unit) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scans yet. Tap Scan to start.")
            }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.name }) { scan -> ScanCard(scan, onOpen) }
        }
    }

    @Composable
    fun ScanCard(scan: ScanItem, onOpen: (ScanItem) -> Unit) {
        val ctx = LocalContext.current
        val clip = LocalClipboardManager.current
        var menu by remember { mutableStateOf(false) }
        var rename by remember { mutableStateOf(false) }
        var delete by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(scan) },
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = scan.pages.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(scan.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(scan.date)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("${scan.pages.size} page(s)", style = MaterialTheme.typography.bodySmall)
                    Row {
                        IconButton(onClick = { sharePdf(ctx, scan) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            clip.setText(AnnotatedString(scan.text))
                            Toast.makeText(ctx, "Text copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Save pages as JPEG") },
                                    onClick = {
                                        menu = false
                                        val n = vm.saveToGallery(scan)
                                        Toast.makeText(ctx, "Saved $n image(s) to Pictures/SmartScan", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print") },
                                    onClick = { menu = false; printPdf(ctx, scan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { menu = false; rename = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menu = false; delete = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (rename) {
            var newName by remember { mutableStateOf(scan.name) }
            AlertDialog(
                onDismissRequest = { rename = false },
                title = { Text("Rename") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (!vm.rename(scan, newName)) {
                            Toast.makeText(ctx, "Invalid or duplicate name", Toast.LENGTH_SHORT).show()
                        }
                        rename = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
            )
        }
        if (delete) {
            AlertDialog(
                onDismissRequest = { delete = false },
                title = { Text("Delete scan?") },
                text = { Text(scan.name) },
                confirmButton = {
                    TextButton(onClick = { vm.delete(scan); delete = false }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } }
            )
        }
    }

    @Composable
    fun Detail(scan: ScanItem) {
        val ctx = LocalContext.current
        val clip = LocalClipboardManager.current
        var tab by remember { mutableIntStateOf(0) }
        val ocrText = remember(scan.name) { scan.text }

        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pages") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Text (OCR)") })
            }
            if (tab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scan.pages) { page ->
                        AsyncImage(
                            model = page,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clip.setText(AnnotatedString(ocrText))
                            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                        OutlinedButton(onClick = { shareText(ctx, ocrText) }) { Text("Share text") }
                    }
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer(Modifier.weight(1f)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                            Text(ocrText.ifBlank { "No text found in this scan." })
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Banner() {
        AndroidView(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            factory = { c: Context ->
                AdView(c).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdManager.BANNER_ID
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
