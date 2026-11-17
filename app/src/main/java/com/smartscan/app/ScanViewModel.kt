package com.smartscan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScanRepository(app)
    val items = MutableStateFlow(repo.list())
    val busy = MutableStateFlow(false)

    private fun refresh() { items.value = repo.list() }

    fun save(result: GmsDocumentScanningResult, done: () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            withContext(Dispatchers.IO) { repo.save(result) }
            refresh(); busy.value = false; done()
        }
    }
    fun rename(i: ScanItem, n: String): Boolean = repo.rename(i, n).also { refresh() }
    fun delete(i: ScanItem) { repo.delete(i); refresh() }
    fun saveToGallery(i: ScanItem): Int = repo.saveToGallery(i)
}
