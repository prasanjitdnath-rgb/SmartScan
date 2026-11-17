package com.smartscan.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun sharePdf(ctx: Context, item: ScanItem) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", item.pdf)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share PDF"))
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Share text"))
}

fun printPdf(ctx: Context, item: ScanItem) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(item.name, PdfAdapter(item.pdf), PrintAttributes.Builder().build())
}

private class PdfAdapter(val file: File) : PrintDocumentAdapter() {
    override fun onLayout(o: PrintAttributes?, n: PrintAttributes?, c: CancellationSignal?,
                          cb: LayoutResultCallback, e: Bundle?) {
        if (c?.isCanceled == true) { cb.onLayoutCancelled(); return }
        cb.onLayoutFinished(PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
    }
    override fun onWrite(p: Array<out PageRange>?, d: ParcelFileDescriptor, c: CancellationSignal?, cb: WriteResultCallback) {
        FileInputStream(file).use { i -> FileOutputStream(d.fileDescriptor).use { o -> i.copyTo(o) } }
        cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
