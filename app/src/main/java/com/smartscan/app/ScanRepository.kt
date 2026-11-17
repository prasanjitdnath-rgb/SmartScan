package com.smartscan.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanItem(val name: String, val pdf: File, val pages: List<File>, val textFile: File) {
    val date: Long get() = pdf.lastModified()
    val text: String get() = if (textFile.exists()) textFile.readText() else ""
}

class ScanRepository(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "scans").apply { mkdirs() }

    fun list(): List<ScanItem> = dir.listFiles { f -> f.extension == "pdf" }.orEmpty()
        .sortedByDescending { it.lastModified() }.map { item(it.nameWithoutExtension) }

    private fun item(base: String) = ScanItem(base, File(dir, "$base.pdf"), pages(base), File(dir, "$base.txt"))

    private fun pages(base: String): List<File> {
        val rx = Regex("${Regex.escape(base)}_p(\\d+)\\.jpg")
        return dir.listFiles().orEmpty().filter { rx.matches(it.name) }
            .sortedBy { it.name.substringAfterLast("_p").removeSuffix(".jpg").toInt() }
    }

    /** Copies scanner output into app storage and runs OCR on every page. */
    suspend fun save(result: GmsDocumentScanningResult): String {
        val base = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        result.pages.orEmpty().forEachIndexed { i, page ->
            val f = File(dir, "${base}_p${i + 1}.jpg")
            copy(page.imageUri, f)
            try {
                val text = recognizer.process(InputImage.fromFilePath(ctx, Uri.fromFile(f))).await().text
                if (text.isNotBlank()) sb.append(text.trim()).append("\n\n")
            } catch (_: Exception) { }
        }
        File(dir, "$base.txt").writeText(sb.toString().trim())
        result.pdf?.let { copy(it.uri, File(dir, "$base.pdf")) }
        return base
    }

    private fun copy(uri: Uri, to: File) {
        ctx.contentResolver.openInputStream(uri)?.use { i -> to.outputStream().use { o -> i.copyTo(o) } }
    }

    fun rename(item: ScanItem, newName: String): Boolean {
        val n = newName.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "_")
        if (n.isEmpty() || File(dir, "$n.pdf").exists()) return false
        item.pdf.renameTo(File(dir, "$n.pdf"))
        item.textFile.renameTo(File(dir, "$n.txt"))
        item.pages.forEach { it.renameTo(File(dir, it.name.replaceFirst(item.name, n))) }
        return true
    }

    fun delete(item: ScanItem) {
        item.pdf.delete(); item.textFile.delete(); item.pages.forEach { it.delete() }
    }

    fun saveToGallery(item: ScanItem): Int {
        var n = 0
        item.pages.forEach { f ->
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartScan")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return@forEach
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            n++
        }
        return n
    }
}
