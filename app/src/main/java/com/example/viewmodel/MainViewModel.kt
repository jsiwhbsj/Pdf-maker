package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.prefs.SettingsRepository
import com.example.data.prefs.UserSettings
import com.example.data.repository.PdfRepository
import com.example.localization.AppLanguage
import com.example.ui.theme.AccentTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FileFilter(val key: String) {
    ALL("all"),
    RECENT("recent"),
    CREATED("created"),
    IMPORTED("imported")
}

class MainViewModel(
    val repository: PdfRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val allDocuments: StateFlow<List<PdfDocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments: StateFlow<List<PdfDocumentEntity>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<PdfDocumentEntity>> = repository.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _fileFilter = MutableStateFlow(FileFilter.ALL)
    val fileFilter: StateFlow<FileFilter> = _fileFilter.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val filteredDocuments: StateFlow<List<PdfDocumentEntity>> = combine(
        allDocuments,
        recentDocuments,
        searchQuery,
        fileFilter
    ) { all, recent, query, filter ->
        val base = when (filter) {
            FileFilter.ALL -> all
            FileFilter.RECENT -> recent
            FileFilter.CREATED -> all.filter { it.sourceTag != "IMPORTED" }
            FileFilter.IMPORTED -> all.filter { it.sourceTag == "IMPORTED" }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) base else base.filter { it.displayName.lowercase().contains(q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFileFilter(filter: FileFilter) {
        _fileFilter.value = filter
    }

    fun consumeToast() {
        _toastMessage.value = null
    }

    fun showMessage(msg: String) {
        _toastMessage.value = msg
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun toggleLanguage() {
        val next = if (settings.value.language == AppLanguage.ENGLISH) AppLanguage.BENGALI else AppLanguage.ENGLISH
        setLanguage(next)
    }

    fun setAccentTheme(theme: AccentTheme) {
        viewModelScope.launch { settingsRepository.setAccentTheme(theme) }
    }

    fun toggleFavorite(doc: PdfDocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc) }
    }

    fun renameDocument(doc: PdfDocumentEntity, newName: String) {
        viewModelScope.launch {
            val ok = repository.renameDocument(doc, newName)
            _toastMessage.value = if (ok) "Renamed to $newName.pdf" else "Could not rename file"
        }
    }

    fun deleteDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _toastMessage.value = "Deleted ${doc.displayName}.pdf"
        }
    }

    fun importPdf(uri: Uri, onImported: (PdfDocumentEntity) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.importPdfFromUri(uri) }
                .onSuccess { entity ->
                    _toastMessage.value = "Imported ${entity.displayName}.pdf"
                    onImported(entity)
                }
                .onFailure { err ->
                    _toastMessage.value = err.message ?: "Failed to import PDF"
                }
        }
    }

    fun exportDocument(doc: PdfDocumentEntity, destUri: Uri) {
        viewModelScope.launch {
            runCatching { repository.exportPdfToUri(doc, destUri) }
                .onSuccess { _toastMessage.value = "Saved ${doc.displayName}.pdf" }
                .onFailure { _toastMessage.value = it.message ?: "Export failed" }
        }
    }

    fun shareDocument(doc: PdfDocumentEntity) {
        repository.sharePdf(doc)
    }

    class Factory(
        private val repository: PdfRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, settingsRepository) as T
        }
    }
}
